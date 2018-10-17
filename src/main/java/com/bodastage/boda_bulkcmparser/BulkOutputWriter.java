package com.bodastage.boda_bulkcmparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BulkOutputWriter implements Closeable {

    /**
     * A map of MO to printwriter.
     */
    private final GrowingHashMap<String, Entry> outputVsDataTypePWMap = new GrowingHashMap<>(Entry::new);

    /**
     * Separation character for file redirection.
     */
    private final String collideDelmitier;
    
    /**
     * Output directory.
     */
    private final String outputDirectory;

    public BulkOutputWriter(String collideDelim, String directoryName) {
    	this.collideDelmitier = collideDelim;
    	this.outputDirectory = directoryName;
    }
    
    /**
     * Writes to an XML file indexed by the provided object type and column headers.
     */
    public synchronized void writeLine(String mo, String paramNames, String paramValues) {
    	Entry entry = outputVsDataTypePWMap.grow(mo);
    	BufferedWriter writer = entry.get(paramNames);
    	
        if (writer == null) {
            File f = new File(outputDirectory, mo + ".csv");
            int i = 0;
            
            while (f.exists()) {
                System.out.printf("Warning: File %s already exists and will be truncated.\n", f.toString());

                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String head = reader.readLine();
                    
                    if (head != null && head.equalsIgnoreCase(paramNames)) {
                        System.out.printf("Warning: Headers matched, so continuing with the same file.\n", f.toString());
                        break;
                    }
                } catch (IOException ignored) { }

                f = new File(outputDirectory, mo + collideDelmitier + ++i + ".csv");
            }

            if (i > 0) {
                System.out.printf("Warning: Redirected output to %s\n", f.toString());
            }

            try {
	            entry.set(paramNames, new BufferedWriter(new FileWriter(f, true)));
	            entry.printLn(paramNames, paramNames);
            } catch (IOException e) {
            	e.printStackTrace();
            }
        }

        entry.printLn(paramNames, paramValues);
    }
    
    @Override
    public void close() throws IOException {
        for (Entry entry : outputVsDataTypePWMap.values()) {
        	entry.close();
        }
        outputVsDataTypePWMap.clear();
    }
    
    private class Entry implements Closeable {

    	private final Map<String, BufferedWriter> writers = new LinkedHashMap<>();
    	
		@Override
		public void close() throws IOException {
			for (BufferedWriter writer : writers.values()) {
	        	writer.close();
	        }
		}
		
		void set(String headers, BufferedWriter writer) {
			writers.put(headers.toLowerCase(), writer);
		}
		
		BufferedWriter get(String headers) {
			headers = headers.toLowerCase();
			return writers.containsKey(headers) ? writers.get(headers) : null;
		}
    	
	    protected void printLn(String headers, String line) {
	    	try {
	    		BufferedWriter writer = get(headers);
	    		writer.write(line);
	    		writer.newLine();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }

    }
}
