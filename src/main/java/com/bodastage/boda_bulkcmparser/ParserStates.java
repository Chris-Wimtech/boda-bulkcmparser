package com.bodastage.boda_bulkcmparser;

/**
 * @author info@bodastage.com
 */
public enum ParserStates {
    /**
     * Managed Object parameters extraction stage.
     */
    EXTRACTING_PARAMETERS,
    /**
     * Parameter value extraction stage
     */
    EXTRACTING_VALUES,
    /**
     * Parsing completed
     */
    EXTRACTING_DONE
}
