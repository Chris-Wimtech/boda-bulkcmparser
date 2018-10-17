package com.bodastage.boda_bulkcmparser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
    /**
     * Current release version
     */
    private static final String VERSION = "1.4.0";

    public static void main(String[] args) {
        try {
            List<String> arguments = Arrays.asList(args);

            //show help
            if (args.length < 2 || arguments.contains("-h")) {
                showHelp();
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
            
            // Read collission fine name delimiter from the command-line
            String collideDelmitier = readOpt("c", arguments, "_");

            //Get bulk CM XML file to parse.
            try (BodaBulkCMParser cmParser = new BodaBulkCMParser(new BulkOutputWriter(collideDelmitier, outputDirectory))) {

	            if (args.length == 3 && new File(args[2]).isFile()) {
                   cmParser.getParametersToExtract(args[2]);
	            }

	            final long startTime = System.currentTimeMillis();
	            cmParser.parse(args[0]);
	            printExecutionTime(startTime);
            }
        } catch(Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Print program's execution time.
     */
    public static void printExecutionTime(long startTime) {
        float runningTime = System.currentTimeMillis() - startTime;

        String s = "Parsing completed.\nTotal time:";

        //Get hours
        if (runningTime > 1000 * 60 * 60) {
            int hrs = (int) Math.floor(runningTime / (1000 * 60 * 60));
            s += hrs + " hours ";
            runningTime -= (hrs * 1000 * 60 * 60);
        }

        //Get minutes
        if (runningTime > 1000 * 60) {
            int mins = (int) Math.floor(runningTime / (1000 * 60));
            s += mins + " minutes ";
            runningTime -= (mins * 1000 * 60);
        }

        //Get seconds
        if (runningTime > 1000) {
            int secs = (int) Math.floor(runningTime / (1000));
            s += secs + " seconds ";
            runningTime -= (secs / 1000);
        }

        //Get milliseconds
        if (runningTime > 0) {
            int msecs = (int) Math.floor(runningTime / (1000));
            s += msecs + " milliseconds ";
            runningTime -= (msecs / 1000);
        }

        System.out.println(s);
    }

    /**
     * Show parser help.
     */
    static void showHelp() {
        System.out.println("boda-bulkcmparser " + VERSION + " Copyright (c) 2018 Bodastage(http://www.bodastage.com)");
        System.out.println("Parses 3GPP Bulk CM XML to csv.");
        System.out.println("Usage: java -jar boda-bulkcmparser.jar <fileToParse.xml|Directory> <outputDirectory> [parameter.conf] [-D] [-c=delimiter]");
    }

    private static String readOpt(String key, List<String> arguments, String def) {
    	for (String i : arguments) {
    		if (i.indexOf("-" + key + "=") == 0) {
    			return i.replace("-" + key + "=", "");
    		}
    	}
    	return def;
    }
}
