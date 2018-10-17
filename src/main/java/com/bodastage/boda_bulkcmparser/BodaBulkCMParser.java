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
     * Tracks XML attributes per Management Objects.
     */
    private final GrowingHashMap<Integer, Map<String, String>> xmlAttrStack = new GrowingHashMap<>(LinkedHashMap::new);

    /**
     * Tracks Managed Object specific 3GPP attributes.
     *
     * This tracks everything within <xn:attributes>...</xn:attributes>.
     */
    private final GrowingHashMap<Integer, Map<String, String>> threeGPPAttrStack = new GrowingHashMap<>(LinkedHashMap::new);

    /**
     * Marks start of processing per MO attributes.
     *
     * This is set to true when xn:attributes is encountered.
     * It's set to false when the corresponding closing tag is encountered.
     */
    private boolean attrMarker = false;

    /**
     * Tracks the depth of VsDataContainer tags in the XML document hierarchy.
     */
    private int vsDCDepth = 0;

    /**
     * Maps of vsDataContainer instances to vendor specific data types.
     */
    private final Map<String, String> vsDataContainerTypeMap = new LinkedHashMap<>();

    /**
     * Tracks current vsDataType if not null
     */
    @Nullable
    private String vsDataType = null;

    /**
     * vsDataTypes stack.
     */
    private final Map<String, String> vsDataTypeStack = new LinkedHashMap<>();

    /**
     * Real stack to push and pop vsDataType attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     */
    private final Stack<String> vsDataTypeRlStack = new Stack<>();

    /**
     * Real stack to push and pop xn:attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     */
    private final Stack<String> xnAttrRlStack = new Stack<>();

    /**
     * Multi-valued parameter separator.
     */
    private String multiValueSeparetor = ";";

    /**
     * For attributes with children, define parameter-child separator
     */
    private String parentChildAttrSeperator = "_";

    /**
     * Tag data.
     */
    private String tagData = "";

    /**
     * Tracking parameters with children under vsDataSomeMO.
     */
    private final Map<String, String> parentChildParameters = new LinkedHashMap<>();

    /**
     * Tracking parameters with children in xn:attributes.
     */
    private final Map<String, String> attrParentChildMap = new LinkedHashMap<>();

    /**
     * A map of MO to print writers.
     */
    private final BulkOutputWriter outputVsDataTypePWMap;

    /**
     * Tracks Managed Object attributes to write to file. This is dictated by
     * the first instance of the MO found.
     *
     * TODO: Handle this better.
     */
    private final Map<String, DistinctStack<String>> moColumns = new LinkedHashMap<>();

    /**
     * Tracks the IDs of the parent elements
     */
    private final Map<String, DistinctStack<String>> moColumnsParentIds = new LinkedHashMap<>();

    /**
     * A map of 3GPP attributes to the 3GPP MOs
     */
    private final GrowingHashMap<String, DistinctStack<String>> moThreeGPPAttrMap = new GrowingHashMap<>(DistinctStack::new);

    private String dateTime = "";

    private ParserStates parserState = ParserStates.EXTRACTING_PARAMETERS;

    /**
     * parameter selection file
     */
    private String parameterFile = null;

    public BodaBulkCMParser(BulkOutputWriter output) {
    	outputVsDataTypePWMap = output;
    }
    
    /**
     * Extracts parameter list from the parameter file
     */
    public void getParametersToExtract(String filename) throws FileNotFoundException, IOException {
    	parameterFile = filename;
    	
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
               String[] moAndParameters =  line.split(":");
               
               DistinctStack<String> parameterStack = new DistinctStack<>(moAndParameters[1].split(","));
               
               String mo = moAndParameters[0];

               if (mo.startsWith("vsData")) {
                    moColumns.put(mo, parameterStack);
                    moColumnsParentIds.put(mo, new DistinctStack());
               } else {
                    moThreeGPPAttrMap.put(mo, parameterStack);
               }
            }

            //Move to the parameter value extraction stage
            //parserState = ParserStates.EXTRACTING_VALUES;
        }
    }

    @Override
    public void parseFile(String inputFilename) throws FileNotFoundException, XMLStreamException, UnsupportedEncodingException {
    	if (parserState == ParserStates.EXTRACTING_PARAMETERS) {
            System.out.println("Extracting parameters from " + getFileName() + "...");
        } else {
            System.out.println("Parsing " + getFileName() + "...");
        }
    	
        super.parseFile(inputFilename);
        
        System.out.println("Done.");
    }

    /**
     * Parser entry point
     */
    @Override
    public void parse(String dataSource) throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException {
        //Extract parameters
        if (parserState == ParserStates.EXTRACTING_PARAMETERS) {
            super.parse(dataSource);
            parserState = ParserStates.EXTRACTING_VALUES;
        }

        //Extracting values
        if (parserState == ParserStates.EXTRACTING_VALUES) {
        	super.parse(dataSource);
            parserState = ParserStates.EXTRACTING_DONE;
        }
    }
    
    @Override
    protected void reset() {
    	super.reset();
    	
        vsDataType = null;
        vsDataTypeStack.clear();
        vsDataTypeRlStack.clear();
        xmlAttrStack.clear();
        attrMarker = false;
    }
    
    @Override
    protected void onStartElement(StartElement startElement) {
        String qName = startElement.getName().getLocalPart();
        final String prefix = startElement.getName().getPrefix();

        Iterator<Attribute> attributes = startElement.getAttributes();

        if (qName.equals("fileFooter") && ParserStates.EXTRACTING_PARAMETERS == parserState) {
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if ("dateTime".equals(attribute.getName().toString())) {
                    dateTime = attribute.getValue();
                }
            }
        }

        //E1:0. xn:VsDataContainer encountered
        //Push vendor specific MOs to the xmlTagStack
        if (qName.equalsIgnoreCase("VsDataContainer")) {
            vsDCDepth++;
            depth++;

            xmlTagStack.push("VsDataContainer_" + vsDCDepth);

            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if ("id".equals(attribute.getName().toString())) {
                    xmlAttrStack.grow(depth).put("id", attribute.getValue());
                }
            }

            vsDataType = null;
            vsDataTypeStack.clear();
            vsDataTypeRlStack.clear();
            return;
        }

        //E1:1
        if (!prefix.equals("xn") && qName.startsWith("vsData")) {
            vsDataType = qName;
            vsDataContainerTypeMap.put("VsDataContainer_" + vsDCDepth, qName);

            return;
        }

        //E1.2
        if (vsDataType != null) {
            if (!vsDataTypeStack.containsKey(qName)) {
                vsDataTypeStack.put(qName, null);
                vsDataTypeRlStack.push(qName);
            }
            return;
        }

        //E1.3
        if ("attributes".equals(qName)) {
            attrMarker = true;
            return;
        }

        //E1.4
        if (xmlTagStack.contains(qName)) {
        	qName += "_" + (getXMLTagOccurences(qName) + 1);
        } else {
	
	        //E1.5
	        if (attrMarker == true && vsDataType == null) {
	            xnAttrRlStack.push(qName);								//Tracks the hierarchy of tags under xn:attributes
	            threeGPPAttrStack.grow(depth).putIfAbsent(qName, null); //Check if the parameter is already in the stack so we don't overwrite it.
	
	            return;
	        }
        }

        //E1.6 - Push 3GPP Defined MOs to the xmlTagStack
        depth++;
        xmlTagStack.push(qName);

        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();

            xmlAttrStack.grow(depth).put(attribute.getName().getLocalPart(), attribute.getValue());
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

        //E3:1 </xn:VsDataContainer>
        if (qName.equalsIgnoreCase("VsDataContainer")) {
            xmlTagStack.pop();
            xmlAttrStack.remove(depth);
            vsDataContainerTypeMap.remove(Integer.toString(vsDCDepth));
            threeGPPAttrStack.remove(depth);
            vsDCDepth--;
            depth--;
            
            return;
        }

        //3.2 </xn:attributes>
        if ("attributes".equals(qName)) {
            attrMarker = false;

            if (parserState == ParserStates.EXTRACTING_PARAMETERS && vsDataType == null) {
                collectThreeGPPAttributes();
            }
            
            return;
        }

        //E3:3 xx:vsData<VendorSpecificDataType>
        if (qName.startsWith("vsData") && !"VsDataContainer".equalsIgnoreCase(qName) && !"xn".equals(prefix)) { //This skips xn:vsDataType

            if (parserState == ParserStates.EXTRACTING_PARAMETERS) {
                collectVendorAttributes();
            } else {
                printVendorAttributes();
            }

            vsDataType = null;
            vsDataTypeStack.clear();
            return;
        }

        //E3:4
        //Process parameters under <bs:vsDataSomeMO>..</bs:vsDataSomeMo>
        if (vsDataType != null && attrMarker == true) {//We are processing vsData<DataType> attributes
            String newTag = qName;
            String newValue = tagData;

            //Handle attributes with children
            if (parentChildParameters.containsKey(qName)) {//End of parent tag

                //Ware at the end of the parent tag so we remove the mapping
                //as the child values have already been collected in vsDataTypeStack.
                parentChildParameters.remove(qName);

                //The top most value on the stack should be qName
                if (vsDataTypeRlStack.size() > 0) {
                    vsDataTypeRlStack.pop();
                }

                //Remove the parent tag from the stack so that we don't output data for it. Their values are taken care of by their children.
                vsDataTypeStack.remove(qName);

                return;
            }

            //If size is greater than 1, then there is parent with children
            if (vsDataTypeRlStack.size() > 1) {
                int len = vsDataTypeRlStack.size();
                String parentTag = vsDataTypeRlStack.get(len - 2);
                newTag = parentTag + parentChildAttrSeperator + qName;

                parentChildParameters.put(parentTag, qName);		//Store the parent and it's child
                vsDataTypeStack.remove(qName);						//Remove this tag from the tag stack.

            }

            // Handle multi-valued parameters
            if (vsDataTypeStack.containsKey(newTag) && vsDataTypeStack.get(newTag) != null) {
                newValue = vsDataTypeStack.get(newTag) + multiValueSeparetor + tagData;
            }

            // TODO: Handle cases of multi values parameters and parameters with children
            // For now continue as if they do not exist
            vsDataTypeStack.put(newTag, newValue);
            tagData = "";
            
            if (vsDataTypeRlStack.size() > 0) {
                vsDataTypeRlStack.pop();
            }
        }

        //E3.5
        //Process tags under xn:attributes.
        if (attrMarker == true && vsDataType == null) {

            Map<String, String> cMap = threeGPPAttrStack.get(depth);
            
            //Handle attributes with children. Do this when parent end tag is encountered.
            if (attrParentChildMap.containsKey(qName)) {		    //End of parent tag
                attrParentChildMap.remove(qName);					//Remove parent child map
                xnAttrRlStack.pop();

                cMap.remove(qName);									//Remove the parent from the threeGPPAttrStack so we don't output data for it.

                return;
            }

            //Handle parent child attributes. Get the child value
            String newTag = qName;
            
            int xnAttrRlStackLen = xnAttrRlStack.size();
            
            if (xnAttrRlStackLen > 1) {
                String parentXnAttr = xnAttrRlStack.get(xnAttrRlStackLen - 2);
                
                attrParentChildMap.put(parentXnAttr, qName);		//Store parent child map

                if (cMap.containsKey(qName)) {						//Remove the child tag from the 3gpp xnAttribute stack
                    cMap.remove(qName);
                }

                newTag = parentXnAttr + parentChildAttrSeperator + qName;
            }

            //For multi-valued attributes, first check that the tag already exits.
            if (cMap.containsKey(newTag) && cMap.get(newTag) != null) {
            	cMap.put(newTag, cMap.get(newTag) + multiValueSeparetor + tagData);
            } else {
            	cMap.put(newTag, tagData);
            }

            tagData = "";
            xnAttrRlStack.pop();
            return;
        }

        //E3:6 - At this point, the remaining XML elements are 3GPP defined Managed Objects
        if (xmlTagStack.contains(qName)) {
            //TODO: This occurrence check does not appear to be of any use; test and remove if not needed.
        	//String theTag = qName;
            /*int occurrence = getXMLTagOccurences(qName);*/
            //if (occurrence > 1) {
            //    theTag = qName + "_" + occurrence;
            //}

            if (parserState != ParserStates.EXTRACTING_PARAMETERS) {
                print3GPPAttributes();
            }

            xmlTagStack.pop();
            xmlAttrStack.remove(depth);
            threeGPPAttrStack.remove(depth);
            depth--;
        }
    }

    /**
     * Returns 3GPP defined Managed Objects(MOs) and their attribute values.
     * This method is called at the end of processing 3GPP attributes.
     *
     * @version 1.0.0
     * @since 1.0.0
     */
    private void print3GPPAttributes() {

        String mo = xmlTagStack.peek().toString();

        if (parameterFile != null && !moThreeGPPAttrMap.containsKey(mo)) {
            return;
        }

        String paramNames = "FileName,varDateTime";
        String paramValues = getFileName() + "," + dateTime;

        Stack<String> ignoreInParameterFile = new Stack<>();

        //Parent IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {
            //The depth at each xml tag index is index+1
            Map<String, String> pmap = xmlAttrStack.get(i + 1);

            if (pmap == null) {
                continue; //Skip null values
            }

            String parentMO = xmlTagStack.get(i);
            
            //Iterate through the XML attribute tags for the element.
            for (Map.Entry<String, String> meMap : pmap.entrySet()) {
                String pName = parentMO + "_" + meMap.getKey();
                
                paramNames += "," + pName;
                paramValues += "," + CSVUtils.toCSVFormat(meMap.getValue());

                ignoreInParameterFile.push(pName);
            }
        }

        //Some MOs don't have 3GPP attributes e.g. the fileHeader and the fileFooter
        if (moThreeGPPAttrMap.get(mo) != null) {
              //Get 3GPP attributes for MO at the current depth
              Map<String, String> current3GPPAttrs = null;

              if (!threeGPPAttrStack.isEmpty() && threeGPPAttrStack.get(depth) != null) {
                  current3GPPAttrs = threeGPPAttrStack.get(depth);
              }

              for (String aAttr : CSVUtils.sortedColumns(moThreeGPPAttrMap.get(mo))) {
                  //Skip parameters listed in the parameter file that are already in the xmlTagList
                  //Skip fileName and dateTime in the parameter file as they are added by default
                  if (ignoreInParameterFile.contains(aAttr)
                		  || "filename".equalsIgnoreCase(aAttr) || "vardatetime".equalsIgnoreCase(aAttr)) {
                	  continue;
                  }

                  String aValue = "";

                  if (current3GPPAttrs != null && current3GPPAttrs.containsKey(aAttr)){
                      aValue = CSVUtils.toCSVFormat(current3GPPAttrs.get(aAttr));
                  }

                  paramNames += "," + aAttr;
                  paramValues += "," + aValue;
              }
        }

        outputVsDataTypePWMap.writeLine(mo, paramNames, paramValues);
    }

    /**
     * Print vendor specific attributes.
     * The vendor specific attributes start with a vendor specific namespace.
     *
     * @verison 1.0.0
     * @since 1.0.0
     */
    public void printVendorAttributes() {

        if (parameterFile != null && !moColumns.containsKey(vsDataType)) {
            return; //Skip if the MO is not in the parameterFile
        }

        String paramNames = "FileName,varDateTime";
        String paramValues = getFileName() + "," + dateTime;

        Map<String,String> parentIdValues = new LinkedHashMap<>();

        //Parent MO IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {

            //Get parent tag from the stack
            String parentMO = xmlTagStack.get(i).toString();

            //If the parent tag is VsDataContainer, look for the vendor specific MO in the vsDataContainer-to-vsDataType map.
            if (parentMO.startsWith("VsDataContainer")) {
                parentMO = vsDataContainerTypeMap.get(parentMO);
            }

            //The depth at each XML tag in xmlTagStack is given by index+1.
            Map<String, String> m = xmlAttrStack.get(i + 1);
            
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

        List<String> columns = CSVUtils.sortedColumns(moColumns.get(vsDataType));

        //Iterate through the columns already collected
        for (String pName : columns) {

            //Skip parent parameters / parentIds listed in the parameter file
            if ((parameterFile != null && moColumnsParentIds.get(vsDataType).contains(pName))
            		|| "FileName".equals(pName) || "varDateTime".equals(pName)) {
            	continue;
            }

            String pValue = "";
            if (vsDataTypeStack.containsKey(pName)) {
                pValue = CSVUtils.toCSVFormat(vsDataTypeStack.get(pName));
            }

            paramNames += "," + pName;
            paramValues += "," + pValue;
        }

        outputVsDataTypePWMap.writeLine(vsDataType, paramNames, paramValues);
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

        //Skip 3GPP MO if it is not in the parameter file
        if (parameterFile != null && !moThreeGPPAttrMap.containsKey(mo)) {
        	return;
        }

        //The attributes stack can be empty if the MO has no 3GPP attributes
        if (threeGPPAttrStack.isEmpty() || threeGPPAttrStack.get(depth) == null) {
            return;
        }
        
        // Hold the current 3GPP attributes
        Map<String, String> tgppAttrs = threeGPPAttrStack.get(depth);

        if (tgppAttrs == null) {
        	return;
        }
        
        //Initialize if the MO does not exist
        DistinctStack attrs = moThreeGPPAttrMap.grow(mo);
        
        //Get vendor specific attributes
        for (String parameter : tgppAttrs.keySet()) {
            //Only add missing parameter if a paramterFile was not specified.
            //The parameter file parameter list is our only interest in this case
            if (parameterFile == null) {
                attrs.pushIfAbsent(parameter);
            }
        }
    }

    /**
     * Collect parameters for vendor specific mo data
     */
    private void collectVendorAttributes(){

        //If MO is not in the parameter list, then don't continue
        if (parameterFile != null && !moColumns.containsKey(vsDataType)) {
        	return;
        }

        if (!moColumns.containsKey(vsDataType) ) {
            moColumns.put(vsDataType, new DistinctStack<>());
            moColumnsParentIds.put(vsDataType, new DistinctStack<>()); //Holds parent element IDs
        }

        //Only update the moColumns list if the parameterFile is not set else use the list provided in the parameterFile
        if (parameterFile == null) {
        	moColumns.get(vsDataType).pushAll(vsDataTypeStack.keySet());
        }
        
        //Parent IDs
        DistinctStack<String> parentIDStack = moColumnsParentIds.get(vsDataType);
        
        for (int i = 0; i < xmlTagStack.size(); i++) {
            String parentMO = xmlTagStack.get(i);

            //If the parent tag is VsDataContainer, look for the vendor specific MO in the vsDataContainer-to-vsDataType map.
            if (parentMO.startsWith("VsDataContainer")) {
                parentMO = vsDataContainerTypeMap.get(parentMO);
            }

            //The depth at each xml tag index is  index+1
            Map<String, String> pnames = xmlAttrStack.get(i + 1);
            
            if (pnames != null) {
            	// Iterate through the XML attribute tags for the element.
                for (String pName : pnames.keySet()) {
                    parentIDStack.pushIfAbsent(parentMO + "_" + pName);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
    	outputVsDataTypePWMap.close();
    }
}