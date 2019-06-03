package com.bodastage.boda_bulkcmparser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public abstract class AbstractFileParser {

    /**
     * The file we are parsing.
     */
    private String baseFileName = "";


    /**
     * Tracks XML elements.
     */
    protected Stack<String> xmlTagStack = new Stack<>();

    /**
     * Tracks how deep a Management Object is in the XML doc hierarchy.
     */
    protected int depth = 0;

    public String getFileName() {
    	return baseFileName;
    }

    /**
     * Get the number of occurrences of an XML tag in the xmlTagStack.
     *
     * This is used to handle cases where XML elements with the same name are nested.
     */
    protected int getXMLTagOccurences(String tagName) {
        int tagOccurences = 0;

        String regex = "^(" + tagName + "|" + tagName + "_\\d+)$";
        
        for (String tag : xmlTagStack) {
            if (tag.matches(regex)) tagOccurences++;
        }
        
        return tagOccurences;
    }

    /**
     * Determines if the source data file is a regular file or a directory and parses it accordingly
     */
    public void parse(String dataSource) throws XMLStreamException, FileNotFoundException, UnsupportedEncodingException {
    	reset();
    	
        Path file = Paths.get(dataSource);
        
        if (!Files.isReadable(file)) {
        	return;
        }
        
        if (Files.isDirectory(file)) {
            //get all the files from a directory
            for (File f : new File(dataSource).listFiles()) {
                try {
                    parseFile(f.getAbsolutePath());
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Skipping file: " + getFileName() + "\n");
                }
            }
        } else if (Files.isRegularFile(file)) {
            parseFile(dataSource);
        }
    }

    protected void reset() {
    	xmlTagStack.clear();
        depth = 0;
    }
    
    protected void parseFile(String inputFilename) throws FileNotFoundException, XMLStreamException, UnsupportedEncodingException {
    	baseFileName = IOUtils.getFileBasename(inputFilename);
    	
        XMLEventReader eventReader = XMLInputFactory.newInstance().createXMLEventReader(new FileReader(inputFilename));
        
        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();
            
            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    onStartElement(event.asStartElement());
                    break;
                case XMLStreamConstants.SPACE:
                case XMLStreamConstants.CHARACTERS:
                    onCharacters(event.asCharacters());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    onEndElement(event.asEndElement());
                    break;
            }
        }
    }
    
    /**
     * Handle start element event.
     */
    protected abstract void onStartElement(StartElement startElement);
    
    /**
     * Handle character events.
     */
    protected abstract void onCharacters(Characters characters);
    
    protected abstract void onEndElement(EndElement endElement);
}
