/**
 * 3GPP Bulk CM XML to CSV Parser.
 *
 * @author Bodastage<info@bodastage.com>
 * @version 1.0.0
 * @see http://github.com/bodastage/boda-bulkcmparsers
 */
package com.bodastage.boda_bulkcmparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.swing.event.ListSelectionEvent;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Array;

public class BodaBulkCMParser {


    /**
     * Current release version
     *
     * Since 1.3.0
     */
    final String VERSION = "1.3.2";

    /**
     * Tracks XML elements.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Stack xmlTagStack = new Stack();

    /**
     * Tracks how deep a Management Object is in the XML doc hierarchy.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Integer depth = 0;

    /**
     * Tracks XML attributes per Management Objects.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Map<Integer, Map<String, String>> xmlAttrStack = new LinkedHashMap<Integer, Map<String, String>>();

    /**
     * Tracks Managed Object specific 3GPP attributes.
     *
     * This tracks every thing within <xn:attributes>...</xn:attributes>.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Map<Integer, Map<String, String>> threeGPPAttrStack = new LinkedHashMap<Integer, Map<String, String>>();

    /**
     * Marks start of processing per MO attributes.
     *
     * This is set to true when xn:attributes is encountered. It's set to false
     * when the corresponding closing tag is encountered.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    boolean attrMarker = false;

    /**
     * Tracks the depth of VsDataContainer tags in the XML document hierarchy.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    int vsDCDepth = 0;

    /**
     * Maps of vsDataContainer instances to vendor specific data types.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Map<String, String> vsDataContainerTypeMap = new LinkedHashMap<String, String>();

    /**
     * Tracks current vsDataType if not null
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String vsDataType = null;

    /**
     * vsDataTypes stack.
     *
     * @since 1.0.0
     * @version 1.1.0
     */
    Map<String, String> vsDataTypeStack = new LinkedHashMap<String, String>();

    /**
     * Real stack to push and pop vsDataType attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Stack vsDataTypeRlStack = new Stack();

    /**
     * Real stack to push and pop xn:attributes.
     *
     * This is used to track multivalued attributes and attributes with children
     *
     * @since 1.0.2
     * @version 1.0.0
     */
    Stack xnAttrRlStack = new Stack();

    /**
     * Multi-valued parameter separator.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String multiValueSeparetor = ";";

    /**
     * For attributes with children, define parameter-child separator
     *
     * @since 1.0.0
     */
    String parentChildAttrSeperator = "_";

    /**
     * Output directory.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String outputDirectory = "/tmp";

    /**
     * Limit the number of iterations for testing.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    int testCounter = 0;

    /**
     * Start element tag.
     *
     * Use in the character event to determine the data parent XML tag.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String startElementTag = "";

    /**
     * Start element NS prefix.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String startElementTagPrefix = "";

    /**
     * Tag data.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    String tagData = "";

    /**
     * Tracking parameters with children under vsDataSomeMO.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    Map<String, String> parentChildParameters = new LinkedHashMap<String, String>();

    /**
     * Tracking parameters with children in xn:attributes.
     *
     * @since 1.0.2
     * @version 1.0.0
     */
    Map<String, String> attrParentChildMap = new LinkedHashMap<String, String>();

    /**
     * A map of MO to printwriter.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    final Map<String, BufferedWriter> outputVsDataTypePWMap = new LinkedHashMap<String, BufferedWriter>();

    /**
     * Bulk CM XML file name. The file we are parsing.
     */
    String bulkCMXMLFile;

    String bulkCMXMLFileBasename;

    /**
     * Tracks Managed Object attributes to write to file. This is dictated by
     * the first instance of the MO found.
     *
     * @TODO: Handle this better.
     *
     * @since 1.0.3
     * @version 1.0.0
     */
    Map<String, Stack> moColumns = new LinkedHashMap<String, Stack>();

    /**
     * Tracks the IDs of the parent elements
     *
     * @since 1.2.0
     */
    Map<String, Stack> moColumnsParentIds = new LinkedHashMap<String, Stack>();

    /**
     * A map of 3GPP attributes to the 3GPP MOs
     *
     * @since 1.3.0
     */
    Map<String, Stack> moThreeGPPAttrMap = new LinkedHashMap<String, Stack>();

    /**
     * The file/directory to be parsed.
     *
     * @since 1.1.0
     */
    private String dataSource;

    /**
     * The file being parsed.
     *
     * @since 1.1.0
     */
    private String dataFile;

    /**
     * The base file name of the file being parsed.
     *
     * @since 1.1.0
     */
    private String baseFileName = "";

    private String dateTime = "";

    /**
     * Parser start time.
     *
     * @since 1.1.0
     * @version 1.1.0
     */
    final long startTime = System.currentTimeMillis();

    private int parserState = ParserStates.EXTRACTING_PARAMETERS;

    /**
     * parameter selection file
     */
    private String parameterFile = null;

  /**
     * Extract parameter list from  parameter file
     *
     * @param filename
     */
    public  void getParametersToExtract(String filename) throws FileNotFoundException, IOException {

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filename));
            for(String line; (line = br.readLine()) != null; ) {
               String [] moAndParameters =  line.split(":");
               String mo = moAndParameters[0];
               String [] parameters = moAndParameters[1].split(",");

               Stack parameterStack = new Stack();
               for(int i =0; i < parameters.length; i++){
                   parameterStack.push(parameters[i]);
               }

               if(mo.startsWith("vsData")){
                    moColumns.put(mo, parameterStack);
                    moColumnsParentIds.put(mo, new Stack());
               }else{
                    moThreeGPPAttrMap.put(mo, parameterStack);
               }

            }

            //Move to the parameter value extraction stage
            //parserState = ParserStates.EXTRACTING_VALUES;
        } finally {
            if (br != null) br.close();
        }
    }

    /**
     * @param inputFilename
     * @param outputDirectory
     */
    public void parseFile(String inputFilename ) throws FileNotFoundException, XMLStreamException, UnsupportedEncodingException {

        XMLInputFactory factory = XMLInputFactory.newInstance();

        XMLEventReader eventReader = factory.createXMLEventReader(
                new FileReader(inputFilename));
        baseFileName = bulkCMXMLFileBasename =  getFileBasename(inputFilename);

        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    startElementEvent(event);
                    break;
                case XMLStreamConstants.SPACE:
                case XMLStreamConstants.CHARACTERS:
                    characterEvent(event);
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    endELementEvent(event);
                    break;
            }
        }

    }

    public void setParameterFile(String filename){
        parameterFile = filename;
    }

    /**
     * @param args the command line arguments
     *
     * @since 1.0.0
     * @version 1.0.1
     */
    public static void main(String[] args) {

        try {
            List<String> arguments = Arrays.asList(args);

            BodaBulkCMParser theParser = new BodaBulkCMParser();

            //show help
            if (args.length < 2 || arguments.contains("-h")) {
                theParser.showHelp();
                System.exit(1);
            }

            String outputDirectory = args[1];

            //Confirm that the output directory is a directory and has write
            //privileges
            File fOutputDir = new File(outputDirectory);
            if (!fOutputDir.isDirectory()) {
                System.err.println("ERROR: The specified output directory is not a directory!.");
                System.exit(1);
            }

            if (!fOutputDir.canWrite()) {
                System.err.println("ERROR: Cannot write to output directory!");
                System.exit(1);
            }

            // Clear out the output directory
            if (arguments.contains("-D")) {
                for (File child : fOutputDir.listFiles()) {
                    child.delete();
                }
            }

            //Get bulk CM XML file to parse.
            //bulkCMXMLFile = ;
            //outputDirectory = args[1];

            BodaBulkCMParser cmParser = new BodaBulkCMParser();

            if(args.length == 3){
                File f = new File(args[2]);
                if(f.isFile()){
                   cmParser.setParameterFile(args[2]);
                   cmParser.getParametersToExtract(args[2]);
                }
            }

            cmParser.setDataSource(args[0]);
            cmParser.setOutputDirectory(outputDirectory);
            cmParser.parse();
        }catch(Exception e){
            System.out.println(e.getMessage());
            System.exit(1);
        }

    }

    /**
     * Parser entry point
     *
     * @throws XMLStreamException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    public void parse() throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException {
        //Extract parameters
        if (parserState == ParserStates.EXTRACTING_PARAMETERS) {
            processFileOrDirectory();

            parserState = ParserStates.EXTRACTING_VALUES;
        }

        //Reset variables
            vsDataType = null;
            vsDataTypeStack.clear();
            vsDataTypeRlStack.clear();
            xmlAttrStack.clear();
            xmlTagStack.clear();
            startElementTag = null;
            startElementTagPrefix = "";
            attrMarker = false;
            depth = 0;

        //Extracting values
        if (parserState == ParserStates.EXTRACTING_VALUES) {
            processFileOrDirectory();
            parserState = ParserStates.EXTRACTING_DONE;
        }

        closeMOPWMap();

        printExecutionTime();
    }

    /**
     * Determines if the source data file is a regular file or a directory and
     * parses it accordingly
     *
     * @since 1.1.0
     * @version 1.0.0
     * @throws XMLStreamException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    public void processFileOrDirectory()
            throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException {
        //this.dataFILe;
        Path file = Paths.get(this.dataSource);
        boolean isRegularExecutableFile = Files.isRegularFile(file)
                & Files.isReadable(file);

        boolean isReadableDirectory = Files.isDirectory(file)
                & Files.isReadable(file);

        if (isRegularExecutableFile) {
            this.setFileName(this.dataSource);
            baseFileName =  getFileBasename(this.dataFile);
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                System.out.println("Extracting parameters from " + this.baseFileName + "...");
            }else{
                System.out.println("Parsing " + this.baseFileName + "...");
            }
            this.parseFile(this.dataSource);
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                 System.out.println("Done.");
            }else{
                System.out.println("Done.");
                //System.out.println(this.baseFileName + " successfully parsed.\n");
            }
        }

        if (isReadableDirectory) {

            File directory = new File(this.dataSource);

            //get all the files from a directory
            File[] fList = directory.listFiles();

            for (File f : fList) {
                this.setFileName(f.getAbsolutePath());
                try {
                    baseFileName =  getFileBasename(this.dataFile);
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                        System.out.print("Extracting parameters from " + this.baseFileName + "...");
                    }else{
                        System.out.print("Parsing " + this.baseFileName + "...");
                    }

                    //Parse
                    this.parseFile(f.getAbsolutePath());
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                         System.out.println("Done.");
                    }else{
                        System.out.println("Done.");
                        //System.out.println(this.baseFileName + " successfully parsed.\n");
                    }

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Skipping file: " + this.baseFileName + "\n");
                }
            }
        }

    }

    /**
     * Collect MO Parameters
     *
     * @param inputFile
     * @param outputDirectory
     */
    @SuppressWarnings("unused")
    private void collectMOParameters(String inputFile, String outputDirectory) {

        try {
            //Confirm that the output directory is a directory and has write
            //privileges
            File fOutputDir = new File(outputDirectory);
            if (!fOutputDir.isDirectory()) {
                System.err.println("ERROR: The specified output directory is not a directory!.");
                System.exit(1);
            }

            if (!fOutputDir.canWrite()) {
                System.err.println("ERROR: Cannot write to output directory!");
                System.exit(1);
            }


            XMLInputFactory factory = XMLInputFactory.newInstance();

            XMLEventReader eventReader = factory.createXMLEventReader(
                    new FileReader(bulkCMXMLFile));
            bulkCMXMLFileBasename = getFileBasename(bulkCMXMLFile);

            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        startElementEvent(event);
                        break;
                    case XMLStreamConstants.SPACE:
                    case XMLStreamConstants.CHARACTERS:
                        characterEvent(event);
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        endELementEvent(event);
                        break;
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("ERROR:" + e.getMessage());
            System.exit(1);
        } catch (XMLStreamException e) {
            System.err.println("ERROR:" + e.getMessage());
            System.exit(1);
        } catch (UnsupportedEncodingException e) {
            System.err.println("ERROR:" + e.getMessage());
            System.exit(1);
        }

    }

    /**
     * Handle start element event.
     *
     * @param xmlEvent
     *
     * @since 1.0.0
     * @version 1.0.0
     *
     */
    public void startElementEvent(XMLEvent xmlEvent) {

        StartElement startElement = xmlEvent.asStartElement();
        String qName = startElement.getName().getLocalPart();
        String prefix = startElement.getName().getPrefix();

        startElementTag = qName;
        startElementTagPrefix = prefix;

        Iterator<Attribute> attributes = startElement.getAttributes();

        if(qName.equals("fileFooter") && ParserStates.EXTRACTING_PARAMETERS == parserState){
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if (attribute.getName().toString().equals("dateTime")) {
                    dateTime = attribute.getValue();
                }
            }
        }

        //E1:0. xn:VsDataContainer encountered
        //Push vendor speicific MOs to the xmlTagStack
        if (qName.equalsIgnoreCase("VsDataContainer")) {
            vsDCDepth++;
            depth++;

            String vsDCTagWithDepth = "VsDataContainer_" + vsDCDepth;
            xmlTagStack.push(vsDCTagWithDepth);

            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();
                if (attribute.getName().toString().equals("id")) {
                    Map<String, String> m = new LinkedHashMap<String, String>();
                    m.put("id", attribute.getValue());
                    xmlAttrStack.put(depth, m);
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

            String vsDCTagWithDepth = "VsDataContainer_" + vsDCDepth;
            vsDataContainerTypeMap.put(vsDCTagWithDepth, qName);

            return;
        }

        //E1.2
        if (null != vsDataType) {
            if (!vsDataTypeStack.containsKey(qName)) {
                vsDataTypeStack.put(qName, null);
                vsDataTypeRlStack.push(qName);
            }
            return;
        }

        //E1.3
        if (qName.equals("attributes")) {
            attrMarker = true;
            return;
        }

        //E1.4
        if (xmlTagStack.contains(qName)) {
            depth++;
            Integer occurences = getXMLTagOccurences(qName) + 1;
            String newTagName = qName + "_" + occurences;
            xmlTagStack.push(newTagName);

            //Add XML attributes to the XML Attribute Stack.
            //@TODO: This while block is repeated below. The 2 should be combined
            while (attributes.hasNext()) {
                Attribute attribute = attributes.next();

                if (xmlAttrStack.containsKey(depth)) {
                    Map<String, String> mm = xmlAttrStack.get(depth);
                    mm.put(attribute.getName().getLocalPart(), attribute.getValue());
                    xmlAttrStack.put(depth, mm);
                } else {
                    Map<String, String> m = new LinkedHashMap<String, String>();
                    m.put(attribute.getName().getLocalPart(), attribute.getValue());
                    xmlAttrStack.put(depth, m);
                }
            }

            return;
        }

        //E1.5
        if (attrMarker == true && vsDataType == null) {

            //Tracks hierachy of tags under xn:attributes.
            xnAttrRlStack.push(qName);

            Map<String, String> m = new LinkedHashMap<String, String>();
            if (threeGPPAttrStack.containsKey(depth)) {
                m = threeGPPAttrStack.get(depth);

                //Check if the parameter is already in the stack so that we dont
                //over write it.
                if (!m.containsKey(qName)) {
                    m.put(qName, null);
                    threeGPPAttrStack.put(depth, m);
                }
            } else {
                m.put(qName, null); //Initial value null
                threeGPPAttrStack.put(depth, m);
            }
            return;
        }

        //E1.6
        //Push 3GPP Defined MOs to the xmlTagStack
        depth++;
        xmlTagStack.push(qName);
        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();

            if (xmlAttrStack.containsKey(depth)) {
                Map<String, String> mm = xmlAttrStack.get(depth);
                mm.put(attribute.getName().getLocalPart(), attribute.getValue());
                xmlAttrStack.put(depth, mm);
            } else {
                Map<String, String> m = new LinkedHashMap<String, String>();
                m.put(attribute.getName().getLocalPart(), attribute.getValue());
                xmlAttrStack.put(depth, m);
            }
        }
    }

    /**
     * Handle character events.
     *
     * @param xmlEvent
     * @version 1.0.0
     * @since 1.0.0
     */
    public void characterEvent(XMLEvent xmlEvent) {
        Characters characters = xmlEvent.asCharacters();
        if (!characters.isWhiteSpace()) {
            tagData = characters.getData();
        }

    }

    public void endELementEvent(XMLEvent xmlEvent)
            throws FileNotFoundException, UnsupportedEncodingException {
        EndElement endElement = xmlEvent.asEndElement();
        String prefix = endElement.getName().getPrefix();
        String qName = endElement.getName().getLocalPart();

        startElementTag = "";

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
        if (qName.equals("attributes")) {
            attrMarker = false;

            if(parserState == ParserStates.EXTRACTING_PARAMETERS && vsDataType == null ){
                updateThreeGPPAttrMap();
            }
            return;
        }

        //E3:3 xx:vsData<VendorSpecificDataType>
        if (qName.startsWith("vsData") && !qName.equalsIgnoreCase("VsDataContainer")
                && !prefix.equals("xn")) { //This skips xn:vsDataType

            if(ParserStates.EXTRACTING_PARAMETERS == parserState){
                collectVendorMOColumns();
            }else{
                processVendorAttributes();
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
                //as the child values have already been collected in
                //vsDataTypeStack.
                parentChildParameters.remove(qName);

                //The top most value on the stack should be qName
                if (vsDataTypeRlStack.size() > 0) {
                    vsDataTypeRlStack.pop();
                }

                //Remove the parent tag from the stack so that we don't output
                //data for it. It's values are taked care of by its children.
                vsDataTypeStack.remove(qName);

                return;
            }

            //If size is greater than 1, then there is parent with chidren
            if (vsDataTypeRlStack.size() > 1) {
                int len = vsDataTypeRlStack.size();
                String parentTag = vsDataTypeRlStack.get(len - 2).toString();
                newTag = parentTag + parentChildAttrSeperator + qName;

                //Store the parent and it's child
                parentChildParameters.put(parentTag, qName);

                //Remove this tag from the tag stack.
                vsDataTypeStack.remove(qName);

            }

            //Handle multivalued paramenters
            if (vsDataTypeStack.containsKey(newTag)) {
                if (vsDataTypeStack.get(newTag) != null) {
                    newValue = vsDataTypeStack.get(newTag) + multiValueSeparetor + tagData;
                }
            }

            //@TODO: Handle cases of multi values parameters and parameters with children
            //For now continue as if they do not exist
            vsDataTypeStack.put(newTag, newValue);
            tagData = "";
            if (vsDataTypeRlStack.size() > 0) {
                vsDataTypeRlStack.pop();
            }
        }

        //E3.5
        //Process tags under xn:attributes.
        if (attrMarker == true && vsDataType == null) {
            String newValue = tagData;
            String newTag = qName;

            //Handle attributes with children.Do this when parent end tag is
            //encountered.
            if (attrParentChildMap.containsKey(qName)) { //End of parent tag
                //Remove parent child map
                attrParentChildMap.remove(qName);

                //Remove the top most value from the stack.
                xnAttrRlStack.pop();

                //Remove the parent from the threeGPPAttrStack so that we
                //don't output data for it.
                Map<String, String> treMap = threeGPPAttrStack.get(depth);
                treMap.remove(qName);
                threeGPPAttrStack.put(depth, treMap);

                return;
            }

            //Handle parent child attributes. Get the child value
            int xnAttrRlStackLen = xnAttrRlStack.size();
            if (xnAttrRlStackLen > 1) {
                String parentXnAttr
                        = xnAttrRlStack.get(xnAttrRlStackLen - 2).toString();
                newTag = parentXnAttr + parentChildAttrSeperator + qName;

                //Store parent child map
                attrParentChildMap.put(parentXnAttr, qName);

                //Remove the child tag from the 3gpp xnAttribute stack
                Map<String, String> cMap = threeGPPAttrStack.get(depth);
                if (cMap.containsKey(qName)) {
                    cMap.remove(qName);
                    threeGPPAttrStack.put(depth, cMap);
                }

            }

            Map<String, String> m = new LinkedHashMap<String, String>();
            m = threeGPPAttrStack.get(depth);

            //For multivaluted attributes , first check that the tag already
            //exits.
            if (m.containsKey(newTag) && m.get(newTag) != null) {
                String oldValue = m.get(newTag);
                String val = oldValue + multiValueSeparetor + newValue;
                m.put(newTag, val);
            } else {
                m.put(newTag, newValue);
            }

            threeGPPAttrStack.put(depth, m);
            tagData = "";
            xnAttrRlStack.pop();
            return;
        }

        //E3:6
        //At this point, the remaining XML elements are 3GPP defined Managed
        //Objects
        if (xmlTagStack.contains(qName)) {
            //String theTag = qName;

            //@TODO: This occurences check does not appear to be of any use; test
            // and remove if not needed.
            /*int occurences = */getXMLTagOccurences(qName);
            //if (occurences > 1) {
            //    theTag = qName + "_" + occurences;
            //}

            if( parserState != ParserStates.EXTRACTING_PARAMETERS){
                process3GPPAttributes();
            }

            xmlTagStack.pop();
            xmlAttrStack.remove(depth);
            threeGPPAttrStack.remove(depth);
            depth--;
        }
    }

    /**
     * Get the number of occurrences of an XML tag in the xmlTagStack.
     *
     * This is used to handle cases where XML elements with the same name are
     * nested.
     *
     * @param tagName String The XML tag name
     * @since 1.0.0
     * @version 1.0.0
     * @return Integer Number of tag occurrences.
     */
    public Integer getXMLTagOccurences(String tagName) {
        int tagOccurences = 0;
        Iterator<String> iter = xmlTagStack.iterator();
        while (iter.hasNext()) {
            String tag = iter.next();
            String regex = "^(" + tagName + "|" + tagName + "_\\d+)$";

            if (tag.matches(regex)) {
                tagOccurences++;
            }
        }
        return tagOccurences;
    }

    /**
     * Returns 3GPP defined Managed Objects(MOs) and their attribute values.
     * This method is called at the end of processing 3GPP attributes.
     *
     * @version 1.0.0
     * @since 1.0.0
     */
    public void process3GPPAttributes()
            throws FileNotFoundException, UnsupportedEncodingException {

        String mo = xmlTagStack.peek().toString();

        if(parameterFile != null && !moThreeGPPAttrMap.containsKey(mo)){
            return;
        }

        String paramNames = "FileName,varDateTime";
        String paramValues = bulkCMXMLFileBasename + "," + dateTime;

        Stack<String> ignoreInParameterFile = new Stack();

        //Parent IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {
            String parentMO = xmlTagStack.get(i).toString();

            //The depth at each xml tag index is  index+1
            int depthKey = i + 1;

            //Iterate through the XML attribute tags for the element.
            if (xmlAttrStack.get(depthKey) == null) {
                continue; //Skip null values
            }

            Iterator<Map.Entry<String, String>> mIter
                    = xmlAttrStack.get(depthKey).entrySet().iterator();

            while (mIter.hasNext()) {
                Map.Entry<String, String> meMap = mIter.next();
                String pName = parentMO + "_" + meMap.getKey();
                paramNames += "," + pName;
                paramValues += "," + toCSVFormat(meMap.getValue());

                ignoreInParameterFile.push(pName);
            }
        }

        //Some MOs dont have 3GPP attributes e.g. the fileHeader
        //and the fileFooter
        if( moThreeGPPAttrMap.get(mo) != null ){
            //Get 3GPP attributes for MO at the current depth
              List<String> a3GPPAtrr = sortedColumns(moThreeGPPAttrMap.get(mo));

              Map<String,String> current3GPPAttrs = null;

              if (!threeGPPAttrStack.isEmpty() && threeGPPAttrStack.get(depth) != null) {
                  current3GPPAttrs = threeGPPAttrStack.get(depth);
              }

              for(int i =0; i < a3GPPAtrr.size();i++){
                  String aAttr = (String)a3GPPAtrr.get(i);

                  //Skip parameters listed in the parameter file that are in the xmlTagList already
                  if(ignoreInParameterFile.contains(aAttr)) continue;

                  //Skip fileName, and dateTime in the parameter file as they are added by default
                  if(aAttr.toLowerCase().equals("filename") ||
                          aAttr.toLowerCase().equals("vardatetime") ) continue;

                  String aValue= "";

                  if( current3GPPAttrs != null && current3GPPAttrs.containsKey(aAttr)){
                      aValue = toCSVFormat(current3GPPAttrs.get(aAttr));
                  }

                  paramNames += "," + aAttr;
                  paramValues += "," + aValue;
              }
         }

        writeLine(mo, paramNames, paramValues);
    }

    List<String> sortedColumns(Stack<String> unsorted) {
        List<String> sss = Arrays.asList(unsorted.toArray(new String[unsorted.size()]));

        sss.sort(Comparator.<String>naturalOrder());

        return sss;
    }

    protected synchronized void writeLine(String mo, String paramNames, String paramValues) {
        if (!outputVsDataTypePWMap.containsKey(mo)) {
            File f = new File(outputDirectory, mo + ".csv");
            int i = 0;
            while (f.exists()) {
                System.out.printf("Warning: File %s already exists and will be truncated.\n", f.toString());

                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(f));
                    String head = reader.readLine();
                    if (head != null && head.toLowerCase().contentEquals(paramValues.toLowerCase())) {
                        System.out.printf("Warning: Headers matched, so continuing with the same file.\n", f.toString());
                        break;
                    }
                } catch (IOException e) { } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) { }
                    }
                }

                f = new File(outputDirectory, mo + "_" + ++i + ".csv");
            }

            if (i > 0) {
                System.out.printf("Warning: Redirected output to %s\n", f.toString());
            }

            try {
                outputVsDataTypePWMap.put(mo, new BufferedWriter(new FileWriter(f, true)));
                outputVsDataTypePWMap.get(mo).write(paramNames);
                outputVsDataTypePWMap.get(mo).newLine();
            } catch (IOException e) {
                //@TODO: Add logger
                e.printStackTrace();
            }
        }

        try {
            outputVsDataTypePWMap.get(mo).write(paramValues);
            outputVsDataTypePWMap.get(mo).newLine();
        } catch (IOException e) {

        }
    }

    /**
     * Print vendor specific attributes. The vendor specific attributes start
     * with a vendor specific namespace.
     *
     * @verison 1.0.0
     * @since 1.0.0
     */
    public void processVendorAttributes() {

        //Skip if the mo is not in the parameterFile
        if( parameterFile != null && !moColumns.containsKey(vsDataType)){
            return;
        }

        //System.out.println("vsDataType:" + vsDataType);

        String paramNames = "FileName,varDateTime";
        String paramValues = bulkCMXMLFileBasename + "," + dateTime;

        Map<String,String> parentIdValues = new LinkedHashMap<String, String>();

        //System.out.println("xmlTagStack.size:" + xmlTagStack.size());

        //Parent MO IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {

            //Get parent tag from the stack
            String parentMO = xmlTagStack.get(i).toString();

            //The depth at each XML tag in xmlTagStack is given by index+1.
            int depthKey = i + 1;

            //If the parent tag is VsDataContainer, look for the
            //vendor specific MO in the vsDataContainer-to-vsDataType map.
            if (parentMO.startsWith("VsDataContainer")) {
                parentMO = vsDataContainerTypeMap.get(parentMO);
            }

            Map<String, String> m = xmlAttrStack.get(depthKey);
            if (null == m) {
                continue;
            }
            Iterator<Map.Entry<String, String>> aIter
                    = xmlAttrStack.get(depthKey).entrySet().iterator();

            while (aIter.hasNext()) {
                Map.Entry<String, String> meMap = aIter.next();

                String pValue = toCSVFormat(meMap.getValue());
                String pName = parentMO + "_" + meMap.getKey();

                parentIdValues.put(pName, pValue);

            }
        }

        //System.out.println("moColumnsParentIds.get(vsDataType):" + moColumnsParentIds.get(vsDataType) );

        Stack parentIds = moColumnsParentIds.get(vsDataType);
        for (int idx = 0; idx < parentIds.size(); idx++) {

            String pName = (String)parentIds.get(idx);

            String pValue= "";
            if( parentIdValues.containsKey(pName)){
                pValue = parentIdValues.get(pName);
            }

            paramNames += "," + pName;
            paramValues += "," + pValue;
        }

        List<String> columns = sortedColumns(moColumns.get(vsDataType));

        //Iterate through the columns already collected
        for (String pName : columns) {

            //Skip parent parameters/ parentIds listed in the parameter file
            if( parameterFile != null && moColumnsParentIds.get(vsDataType).contains(pName)) continue;

            if(pName.equals("FileName") || pName.equals("varDateTime") ) continue;

            String pValue = "";
            if (vsDataTypeStack.containsKey(pName)) {
                pValue = toCSVFormat(vsDataTypeStack.get(pName));
            }

            paramNames += "," + pName;
            paramValues += "," + pValue;
        }

        writeLine(vsDataType, paramNames, paramValues);
    }


    /**
     * Update the map of 3GPP MOs to attributes.
     *
     * This is necessary to ensure the final output in the csv is aligned.
     *
     * @since 1.3.0
     */
    private void updateThreeGPPAttrMap(){
        if( xmlTagStack == null || xmlTagStack.isEmpty() ) return;


        String mo = xmlTagStack.peek().toString();

        //Skil 3GPP MO if it is not in the parameter file
        if(parameterFile != null && !moThreeGPPAttrMap.containsKey(mo) ) return;

        //Hold the current 3GPP attributes
        HashMap<String, String> tgppAttrs = null;

        Stack attrs =  new Stack();

        //Initialize if the MO does not exist
        if(!moThreeGPPAttrMap.containsKey(mo) ){
            moThreeGPPAttrMap.put(mo, new Stack());
        }


        //The attributes stack can be empty if the MO has no 3GPP attributes
        if(threeGPPAttrStack.isEmpty() || threeGPPAttrStack.get(depth) == null){
            return;
        }
        tgppAttrs = (LinkedHashMap<String, String>) threeGPPAttrStack.get(depth);


        attrs = moThreeGPPAttrMap.get(mo);

        if(tgppAttrs != null){
            //Get vendor specific attributes
            Iterator<Map.Entry<String, String>> iter
                    = tgppAttrs.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, String> me = iter.next();
                String parameter = me.getKey();

                //Only add missing parameter is a paramterFile was not specified.
                //The parameter file parameter list is our only interest in this
                //case
                if( !attrs.contains( parameter ) && parameterFile == null ){
                    attrs.push(parameter);
                }
            }
            moThreeGPPAttrMap.replace(mo, attrs);

        }
    }

    /**
     * Collect parameters for vendor specific mo data
     */
    private void collectVendorMOColumns(){

        //If MO is not in the parameter list, then don't continue
        if(parameterFile != null && !moColumns.containsKey(vsDataType)) return;

        if (!moColumns.containsKey(vsDataType) ) {
            moColumns.put(vsDataType, new Stack());
            moColumnsParentIds.put(vsDataType, new Stack()); //Holds parent element IDs
        }

        Stack s = moColumns.get(vsDataType);
        Stack parentIDStack = moColumnsParentIds.get(vsDataType);

        //Only update hte moColumns list if the parameterFile is not set
        //else use the list provided in the parameterFile
        if( parameterFile == null ){
            //Get vendor specific attributes
            Iterator<Map.Entry<String, String>> iter
                    = vsDataTypeStack.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, String> me = iter.next();
                String parameter = me.getKey();
                if( !s.contains( parameter ) ){
                    s.push(parameter);
                }
            }
            moColumns.replace(vsDataType, s);
        }
        //
        //Parent IDs
        for (int i = 0; i < xmlTagStack.size(); i++) {
            String parentMO = xmlTagStack.get(i).toString();

            //If the parent tag is VsDataContainer, look for the
            //vendor specific MO in the vsDataContainer-to-vsDataType map.
            if (parentMO.startsWith("VsDataContainer")) {
                parentMO = vsDataContainerTypeMap.get(parentMO);
            }

            //The depth at each xml tag index is  index+1
            int depthKey = i + 1;

            //Iterate through the XML attribute tags for the element.
            if (xmlAttrStack.get(depthKey) == null) {
                continue; //Skip null values
            }

            Iterator<Map.Entry<String, String>> mIter
                    = xmlAttrStack.get(depthKey).entrySet().iterator();

            while (mIter.hasNext()) {
                Map.Entry<String, String> meMap = mIter.next();
                //String pName = meMap.getKey();
                String pName = parentMO + "_" + meMap.getKey();

                if( parentIDStack.search(pName ) < 0 ){
                    parentIDStack.push(pName);
                }
            }
        }

        moColumnsParentIds.replace(vsDataType, parentIDStack);

    }

    /**
     * Process given string into a format acceptable for CSV format.
     *
     * @since 1.0.0
     * @param s String
     * @return String Formated version of input string
     */
    public String toCSVFormat(String s) {
        String csvValue = s;

        //Check if value contains comma
        if (s.contains(",")) {
            csvValue = "\"" + s + "\"";
        }

        if (s.contains("\"")) {
            csvValue = "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return csvValue;
    }

    /**
     * Close file print writers.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    public void closeMOPWMap() {
        for (BufferedWriter writer : outputVsDataTypePWMap.values()) {
            try {
                writer.close();
            } catch (IOException e) { }
        }
        outputVsDataTypePWMap.clear();
    }

    /**
     * Show parser help.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    public void showHelp() {
        System.out.println("boda-bulkcmparser "+ VERSION +" Copyright (c) 2018 Bodastage(http://www.bodastage.com)");
        System.out.println("Parses 3GPP Bulk CM XML to csv.");
        System.out.println("Usage: java -jar boda-bulkcmparser.jar <fileToParse.xml|Directory> <outputDirectory> [parameter.conf]");
    }

    /**
     * Get file base name.
     *
     * @since 1.0.0
     */
    public String getFileBasename(String filename) {
        try {
            return new File(filename).getName();
        } catch (Exception e) {
            return filename;
        }
    }

    /**
     * Set name of file to parser.
     *
     * @since 1.0.1
     * @version 1.0.0
     * @param dataSource
     */
    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Print program's execution time.
     *
     * @since 1.0.0
     */
    public void printExecutionTime() {
        float runningTime = System.currentTimeMillis() - startTime;

        String s = "Parsing completed.\n";
        s = s + "Total time:";

        //Get hours
        if (runningTime > 1000 * 60 * 60) {
            int hrs = (int) Math.floor(runningTime / (1000 * 60 * 60));
            s = s + hrs + " hours ";
            runningTime = runningTime - (hrs * 1000 * 60 * 60);
        }

        //Get minutes
        if (runningTime > 1000 * 60) {
            int mins = (int) Math.floor(runningTime / (1000 * 60));
            s = s + mins + " minutes ";
            runningTime = runningTime - (mins * 1000 * 60);
        }

        //Get seconds
        if (runningTime > 1000) {
            int secs = (int) Math.floor(runningTime / (1000));
            s = s + secs + " seconds ";
            runningTime = runningTime - (secs / 1000);
        }

        //Get milliseconds
        if (runningTime > 0) {
            int msecs = (int) Math.floor(runningTime / (1000));
            s = s + msecs + " milliseconds ";
            runningTime = runningTime - (msecs / 1000);
        }

        System.out.println(s);
    }

    /**
     * Set the output directory.
     *
     * @since 1.0.0
     * @version 1.0.0
     * @param directoryName
     */
    public void setOutputDirectory(String directoryName) {
        this.outputDirectory = directoryName;
    }

    /**
     * Set name of file to parser.
     *
     * @since 1.0.0
     * @version 1.0.0
     * @param directoryName
     */
    private void setFileName(String filename) {
        this.dataFile = filename;
    }
}