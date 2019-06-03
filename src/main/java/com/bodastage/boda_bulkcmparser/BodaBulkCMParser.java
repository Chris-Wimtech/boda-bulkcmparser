/**
 * 3GPP Bulk CM XML to CSV Parser.
 *
 * @author Bodastage<info@bodastage.com>
 * @version 1.3.3
 * @see http://github.com/bodastage/boda-bulkcmparsers
 */
package com.bodastage.boda_bulkcmparser;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;

import org.eclipse.jdt.annotation.Nullable;

public class BodaBulkCMParser extends AbstractFileParser implements Closeable {

    /**
     * Multi-valued parameter separator.
     */
    private static final String VALUE_SEPARATOR = ";";

    /**
     * For attributes with children, define parameter-child separator
     */
    private static final String CHILD_ATTRIBUTE_SEPARATOR = "_";
    

    /**
     * Tracks Managed Object specific 3GPP attributes.
     *
     * This tracks everything within <xn:attributes>...</xn:attributes>.
     */
    private final GrowingHashMap<Integer, Map<String, String>> threeGPPAttributes = new GrowingHashMap<>(LinkedHashMap::new);

    /**
     * Maps of vsDataContainer instances to vendor specific data types.
     */
    private final Map<String, String> vsDataContainerTypeMap = new LinkedHashMap<>();

    /**
     * vsDataTypes stack.
     */
    private final Map<String, String> vsDataTypes = new LinkedHashMap<>();
    /**
     * Real stack to push and pop vsDataType attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     */
    private final DistinctStack<String> vsDataTypeAttributes = new DistinctStack<>();
    
    /**
     * Real stack to push and pop xn:attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     */
    private final Stack<String> xnAttributes = new Stack<>();
    /**
     * Tracking parameters with children under vsDataSomeMO.
     */
    private final Map<String, String> parentChildParameters = new LinkedHashMap<>();
    /**
     * Tracking parameters with children in xn:attributes.
     */
    private final Map<String, String> attrParentChildMap = new LinkedHashMap<>();

    /**
     * Tracks XML attributes per Management Objects.
     */
    private final GrowingHashMap<Integer, Map<String, String>> moAttributes = new GrowingHashMap<>(LinkedHashMap::new);
    /**
     * Tracks Managed Object attributes to write to file. This is dictated by
     * the first instance of the MO found.
     *
     * TODO: Handle this better.
     */

    private final GrowingHashMap<String, DistinctStack<String>> moColumns = new GrowingHashMap<>(DistinctStack::new);
    /**
     * Tracks the IDs of the parent elements
     */
    private final GrowingHashMap<String, DistinctStack<String>> moColumnsParentIds = new GrowingHashMap<>(DistinctStack::new);
    /**
     * A map of 3GPP attributes to the 3GPP MOs
     */
    private final GrowingHashMap<String, DistinctStack<String>> moThreeGPPAttributes = new GrowingHashMap<>(DistinctStack::new);

    /**
     * Marks start of processing per MO attributes.
     *
     * This is set to true when xn:attributes is encountered.
     * It's set to false when the corresponding closing tag is encountered.
     */
    private boolean isProcessingMOAttributes = false;

    /**
     * Tracks current vsDataType if not null
     */
    @Nullable
    private String vsDataType = null;

    /**
     * Tag data.
     */
    private String tagData = "";
    private String dateTime = "";
    /**
     * parameter selection file
     */
    private String parameterFile = null;

    /**
     * A map of MO to print writers.
     */
    private final BulkOutputWriter output;

    private ParserStates currentState = ParserStates.EXTRACTING_PARAMETERS;


    public BodaBulkCMParser(BulkOutputWriter output) {
    	this.output = output;
    }
    
    /**
     * Extracts parameter list from the parameter file
     */
    public void loadParametersForExtraction(String filename) throws FileNotFoundException, IOException {
    	parameterFile = filename;
    	
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
               String[] moAndParameters =  line.split(":");
               
               DistinctStack<String> parameterStack = new DistinctStack<>(moAndParameters[1].split(","));
               
               String mo = moAndParameters[0];

               if (mo.startsWith("vsData")) {
                    moColumns.put(mo, parameterStack);
                    moColumnsParentIds.put(mo, new DistinctStack<>());
               } else {
                    moThreeGPPAttributes.put(mo, parameterStack);
               }
            }

            //Move to the parameter value extraction stage
            //parserState = ParserStates.EXTRACTING_VALUES;
        }
    }

    @Override
    protected void parseFile(String inputFilename) throws FileNotFoundException, XMLStreamException, UnsupportedEncodingException {
    	System.out.println("Boda BulkCMParser executed on file " + inputFilename);
    	System.out.println("Stage [" + currentState + "]: Running...");
    	
        super.parseFile(inputFilename);
        
        System.out.println("Stage [" + currentState + "]: Completed.");
    }

    /**
     * Parser entry point
     */
    @Override
    public void parse(String dataSource) throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException {
        if (currentState == ParserStates.EXTRACTING_PARAMETERS) {
            super.parse(dataSource);																					//Extract parameters
            currentState = ParserStates.EXTRACTING_VALUES;
        }

        if (currentState == ParserStates.EXTRACTING_VALUES) {
        	super.parse(dataSource);																					//Extracting values
            currentState = ParserStates.EXTRACTING_DONE;
        }
    }
    
    @Override
    protected void reset() {
    	super.reset();
    	
        vsDataType = null;
        vsDataTypes.clear();
        vsDataTypeAttributes.clear();
        moAttributes.clear();
        
        isProcessingMOAttributes = false;
    }
    
    @Override
    protected void onStartElement(StartElement startElement) {
        String qName = startElement.getName().getLocalPart();
        final String prefix = startElement.getName().getPrefix();

        Iterator<Attribute> attributes = startElement.getAttributes();

        if ("fileFooter".equals(qName) && ParserStates.EXTRACTING_PARAMETERS == currentState) {
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if ("dateTime".equals(attribute.getName().toString())) {
                    dateTime = attribute.getValue();
                }
            }
        }
        
        if ("VsDataContainer".equalsIgnoreCase(qName)) {													//E1:0. xn:VsDataContainer encountered Push vendor specific MOs to the xmlTagStack
            depth++;

            xmlTagStack.push("VsDataContainer_" + depth);

            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if ("id".equals(attribute.getName().toString())) {
                    moAttributes.grow(depth).put("id", attribute.getValue());
                }
            }

            vsDataType = null;
            vsDataTypes.clear();
            vsDataTypeAttributes.clear();
            return;
        }
        																									//E1:1
        if (!"xn".equalsIgnoreCase(prefix) && qName.startsWith("vsData")) {
            vsDataType = qName;
            vsDataContainerTypeMap.put("VsDataContainer_" + depth, qName);

            return;
        }
       																										//E1.2
        if (vsDataType != null) {
            if (!vsDataTypes.containsKey(qName)) {
                vsDataTypes.put(qName, null);
                vsDataTypeAttributes.push(qName);
            }
            return;
        }
        																									//E1.3
        if ("attributes".equals(qName)) {
            isProcessingMOAttributes = true;
            return;
        }
    																										//E1.4
        if (xmlTagStack.contains(qName)) {
        	qName += "_" + (getXMLTagOccurences(qName) + 1);
        } else {																							//E1.5
	        if (isProcessingMOAttributes == true && vsDataType == null) {
	            xnAttributes.push(qName);																	//Tracks the hierarchy of tags under xn:attributes
	            threeGPPAttributes.grow(depth).putIfAbsent(qName, null);									//Check if the parameter is already in the stack so we don't overwrite it.
	
	            return;
	        }
        }
        																									//E1.6 - Push 3GPP Defined MOs to the xmlTagStack
        depth++;
        xmlTagStack.push(qName);

        Map<String, String> arts = moAttributes.grow(depth);
        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();

            arts.put(attribute.getName().getLocalPart(), attribute.getValue());
        }
    }
    
    @Override
    protected void onCharacters(Characters characters) {
        if (!characters.isWhiteSpace()) {
            tagData = characters.getData();
        }
    }

    @Override
    protected void onEndElement(EndElement endElement) {
        final String prefix = endElement.getName().getPrefix();
        final String qName = endElement.getName().getLocalPart();

        final boolean isVsDataContainer = "VsDataContainer".equalsIgnoreCase(qName);
        
        if (isVsDataContainer) {															//E3:1 - </xn:VsDataContainer>
            xmlTagStack.pop();
            moAttributes.remove(depth);
            vsDataContainerTypeMap.remove(Integer.toString(depth));
            threeGPPAttributes.remove(depth);
            depth--;
            
            return;
        }

        if ("attributes".equals(qName)) {													//E3.2 - </xn:attributes>
            isProcessingMOAttributes = false;

            if (currentState == ParserStates.EXTRACTING_PARAMETERS && vsDataType == null) {
                collectThreeGPPAttributes();
            }
            
            return;
        }
    																						//E3:3 - xx:vsData<VendorSpecificDataType>
        if (qName.startsWith("vsData") && !isVsDataContainer && !"xn".equals(prefix)) { 	//This skips xn:vsDataType
            if (currentState == ParserStates.EXTRACTING_PARAMETERS) {
                collectVendorAttributes();
            } else {
                printVendorAttributes();
            }

            vsDataType = null;
            vsDataTypes.clear();
            return;
        }
        																					//E3:4 - Process parameters under <bs:vsDataSomeMO>..</bs:vsDataSomeMo>
        if (vsDataType != null && isProcessingMOAttributes == true) {						//We are processing vsData<DataType> attributes
            																				//Handle attributes with children
            if (parentChildParameters.containsKey(qName)) {									//End of parent tag
                parentChildParameters.remove(qName);										//We're at the end of the parent tag so we remove the mapping as the child values have already been collected in vsDataTypeStack.
                vsDataTypeAttributes.popIfPresent();										//The top most value on the stack should be qName
                vsDataTypes.remove(qName);													//Remove the parent tag from the stack so that we don't output data for it. Their values are taken care of by their children.

                return;
            }

            String newTag = qName;
            
            if (vsDataTypeAttributes.size() > 1) {											 //There is a parent with children
                String parentTag = vsDataTypeAttributes.get(vsDataTypeAttributes.size() - 2);
                
                newTag = parentTag + CHILD_ATTRIBUTE_SEPARATOR + qName;

                parentChildParameters.put(parentTag, qName);								//Store the parent and it's child
                vsDataTypes.remove(qName);													//Remove this tag from the tag stack.

            }

            String data = vsDataTypes.getOrDefault(newTag, null);
            if (data == null) data = "";
            if (!data.isEmpty()) data += VALUE_SEPARATOR;									// Handle multi-valued parameters
            data += tagData;

            // TODO: Handle cases of multi values parameters and parameters with children. For now continue as if they don't exist
            vsDataTypes.put(newTag, data);
            tagData = "";
            
            vsDataTypeAttributes.popIfPresent();
        }

        //E3.5
        //Process tags under xn:attributes.
        if (isProcessingMOAttributes == true && vsDataType == null) {
            Map<String, String> cMap = threeGPPAttributes.get(depth);						//Handle attributes with children. Do this when parent end tag is encountered.

            if (attrParentChildMap.containsKey(qName)) {									//Remove the parent from the threeGPPAttrStack so we don't output data for it.
                attrParentChildMap.remove(qName);
                xnAttributes.pop();

                cMap.remove(qName);

                return;
            }

            String newTag = qName;															//Handle parent child attributes. Get the child value
            
            int xnAttrRlStackLen = xnAttributes.size();
            
            if (xnAttrRlStackLen > 1) {
                String parentXnAttr = xnAttributes.get(xnAttrRlStackLen - 2);
                
                attrParentChildMap.put(parentXnAttr, qName);								//Store parent child map

                cMap.remove(qName);															//Remove the child tag from the 3gpp xnAttribute stack

                newTag = parentXnAttr + CHILD_ATTRIBUTE_SEPARATOR + qName;
            }

            String data = cMap.getOrDefault(newTag, null);									//For multi-valued attributes, first check if the tag already exits and append new ones
            
            if (data == null) data = "";
            if (!data.isEmpty()) data += VALUE_SEPARATOR;
            cMap.put(newTag, data + tagData);
            
            
            tagData = "";
            xnAttributes.pop();
            return;
        }

        if (xmlTagStack.contains(qName)) {													//E3:6 - At this point, the remaining XML elements are 3GPP defined Managed Objects
            //TODO: This occurrence check does not appear to be of any use; test and remove if not needed.
        	//String theTag = qName;
            /*int occurrence = getXMLTagOccurences(qName);*/
            //if (occurrence > 1) {
            //    theTag = qName + "_" + occurrence;
            //}

            if (currentState != ParserStates.EXTRACTING_PARAMETERS) {
                print3GPPAttributes();
            }

            xmlTagStack.pop();
            moAttributes.remove(depth);
            threeGPPAttributes.remove(depth);
            depth--;
        }
    }

    /**
     * Returns 3GPP defined Managed Objects(MOs) and their attribute values.
     * This method is called at the end of processing 3GPP attributes.
     */
    private void print3GPPAttributes() {

        String mo = xmlTagStack.peek();

        if (parameterFile != null && !moThreeGPPAttributes.containsKey(mo)) {
            return;
        }

        String paramNames = "FileName,varDateTime";
        String paramValues = getFileName() + "," + dateTime;

        Stack<String> ignoreInParameterFile = new Stack<>();

        for (int i = 0; i < xmlTagStack.size(); i++) {															//Parent IDs
            Map<String, String> attr = moAttributes.get(i + 1);													//The depth at each xml tag index is index+1

            if (attr != null) {
            	String parentMO = xmlTagStack.get(i);
                																								//Iterate through the XML attribute tags for the element.
                for (Map.Entry<String, String> entry : attr.entrySet()) {
                    String pName = parentMO + "_" + entry.getKey();
                    
                    paramNames += "," + pName;
                    paramValues += "," + CSVUtils.toCSVFormat(entry.getValue());

                    ignoreInParameterFile.push(pName);
                }
            }
        }

        if (moThreeGPPAttributes.get(mo) != null) {																//Some MOs don't have 3GPP attributes e.g. the fileHeader and the fileFooter
              Map<String, String> attrs = threeGPPAttributes.get(depth);										//Get 3GPP attributes for MO at the current depth

              for (String aAttr : CSVUtils.sortedColumns(moThreeGPPAttributes.get(mo))) {
                  if (ignoreInParameterFile.contains(aAttr)														//Skip parameters listed in the parameter file that are already in the xmlTagList
                		  || "filename".equalsIgnoreCase(aAttr) || "vardatetime".equalsIgnoreCase(aAttr)) {		//Skip fileName and dateTime in the parameter file as they are added by default
                	  continue;
                  }

                  String aValue = "";

                  if (attrs != null && attrs.containsKey(aAttr)){
                      aValue = CSVUtils.toCSVFormat(attrs.get(aAttr));
                  }

                  paramNames += "," + aAttr;
                  paramValues += "," + aValue;
              }
        }

        output.writeLine(mo, paramNames, paramValues);
    }

    /**
     * Prints vendor specific attributes.
     * The vendor specific attributes start with a vendor specific namespace.
     */
    private void printVendorAttributes() {
        if (parameterFile != null && !moColumns.containsKey(vsDataType)) {
            return;																							//Skip if the MO is not in the parameterFile
        }

        String paramNames = "FileName,varDateTime";
        String paramValues = getFileName() + "," + dateTime;

        Map<String,String> parentIdValues = new LinkedHashMap<>();

        //Parent MO IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {
            String parentMO = xmlTagStack.get(i);															//Get the parent tag from the stack

            if (parentMO.startsWith("VsDataContainer")) {
                parentMO = vsDataContainerTypeMap.get(parentMO);											//If the parent tag is VsDataContainer, look for the vendor specific MO in the vsDataContainer-to-vsDataType map.			
            }

            Map<String, String> m = moAttributes.get(i + 1);												//The depth at each XML tag in xmlTagStack is given by index+1.
            
            if (m != null) {
                for (Map.Entry<String, String> meMap : m.entrySet()) {
                    parentIdValues.put(parentMO + "_" + meMap.getKey(), CSVUtils.toCSVFormat(meMap.getValue()));
                }
            }
        }

        for (String pName : moColumnsParentIds.get(vsDataType)) {
            paramNames += "," + pName;
            paramValues += "," + parentIdValues.getOrDefault(pName, "");
        }

        for (String pName : CSVUtils.sortedColumns(moColumns.get(vsDataType))) {							//Iterate through the columns already collected
            if ((parameterFile != null && moColumnsParentIds.get(vsDataType).contains(pName))				//Skip parent parameters / parentIds listed in the parameter file
            		|| "FileName".equals(pName) || "varDateTime".equals(pName)) {
            	continue;
            }

            String pValue = "";
            
            if (vsDataTypes.containsKey(pName)) {
                pValue = CSVUtils.toCSVFormat(vsDataTypes.get(pName));
            }

            paramNames += "," + pName;
            paramValues += "," + pValue;
        }

        output.writeLine(vsDataType, paramNames, paramValues);
    }

    /**
     * Update the map of 3GPP MOs to attributes.
     *
     * This is necessary to ensure the final output in the csv is aligned.
     */
    private void collectThreeGPPAttributes(){
        if (xmlTagStack.isEmpty()) {
        	return;
        }

        String mo = xmlTagStack.peek();

        if ((parameterFile != null && !moThreeGPPAttributes.containsKey(mo))							//Skip 3GPP MO if it is not in the parameter file
    		|| threeGPPAttributes.isEmpty()) {															//The attributes stack can be empty if the MO has no 3GPP attributes
        	return;
        }

        Map<String, String> attrs = threeGPPAttributes.get(depth);										// Holds the current 3GPP attributes

        if (attrs == null) {
        	return;
        }
        
        if (parameterFile == null) {																	//Only add missing parameter if a paramterFile was not specified. The parameter file parameter list is our only interest in this case
        	moThreeGPPAttributes.grow(mo).pushAll(attrs.keySet());										//Initialize if the MO does not exist
    	}
    }

    /**
     * Collect parameters for vendor specific mo data
     */
    private void collectVendorAttributes(){
        if (parameterFile != null && !moColumns.containsKey(vsDataType)) {
        	return;																						//If MO is not in the parameter list, then don't continue
        }

        moColumns.grow(vsDataType);

        if (parameterFile == null) {																	//Only update the moColumns list if the parameterFile is not set else use the list provided in the parameterFile
        	moColumns.get(vsDataType).pushAll(vsDataTypes.keySet());
        }
        																								//Parent IDs
        DistinctStack<String> parentIDStack = moColumnsParentIds.grow(vsDataType);						//Holds parent element IDs
        
        for (int i = 0; i < xmlTagStack.size(); i++) {
            String parentMO = xmlTagStack.get(i);

            if (parentMO.startsWith("VsDataContainer")) {												//If the parent tag is VsDataContainer, look for the vendor specific MO in the vsDataContainer-to-vsDataType map.
                parentMO = vsDataContainerTypeMap.get(parentMO);
            }

            Map<String, String> pnames = moAttributes.get(i + 1);
            
            if (pnames != null) {
                for (String pName : pnames.keySet()) {													// Iterate through the XML attribute tags for the element.
                    parentIDStack.pushIfAbsent(parentMO + CHILD_ATTRIBUTE_SEPARATOR + pName);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
    	output.close();
    }
}