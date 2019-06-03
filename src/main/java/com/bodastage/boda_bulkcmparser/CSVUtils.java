package com.bodastage.boda_bulkcmparser;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

public class CSVUtils {

    /**
     * Processes the given string into a format acceptable for CSV insertion.
     */
    public static String toCSVFormat(String s) {
        boolean quote = s.contains("\"");

        if (s.contains(",") || quote) {
	        return "\"" + (quote ? s.replace("\"", "\"\"") : s) + "\"";
        }

        return s;
    }

    public static List<String> sortedColumns(Stack<String> unsorted) {
        List<String> sss = Arrays.asList(unsorted.toArray(new String[unsorted.size()]));

        sss.sort(Comparator.<String>naturalOrder());

        return sss;
    }

}
