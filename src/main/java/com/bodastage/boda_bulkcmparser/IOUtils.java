package com.bodastage.boda_bulkcmparser;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class IOUtils {

    /**
     * Get file base name.
     */
    public static String getFileBasename(String filename) {
        try {
            return new File(filename).getName();
        } catch (Exception e) {
            return filename;
        }
    }
    
    public static void closeQuietly(Closeable c) {
    	try {
    		if (c != null) c.close();
    	} catch (IOException ignored) {}
    }

}
