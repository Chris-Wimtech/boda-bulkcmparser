package com.bodastage.boda_bulkcmparser;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

public class CSVUtils {

    /**
     * Process given string into a format acceptable for CSV format.
     *
     * @since 1.0.0
     * @param s String
     * @return String Formated version of input string
     */
    public static String toCSVFormat(String s) {
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

    public static List<String> sortedColumns(Stack<String> unsorted) {
        List<String> sss = Arrays.asList(unsorted.toArray(new String[unsorted.size()]));

        sss.sort(Comparator.<String>naturalOrder());

        return sss;
    }

}
