/*
 * Me Context
 */
package com.bodastage.boda_bulkcmparser.bulkcmxml;

import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @version 1.0.0
 * @author Bodastage<info@bodastage.com>
 */
class MeContextAttributes {
    
    private String dnPrefix = "www.bodastage.com";
    
    private VsDataContainer vsDataContainer = new VsDataContainer();

    public String getDnPrefix() {
        return dnPrefix;
    }

    @XmlElement(name = "dnPrefix", namespace = "http://www.3gpp.org/ftp/specs/archive/32_series/32.625#genericNrm")
    public void setDnPrefix(String dnPrefix) {
        this.dnPrefix = dnPrefix;
    }

    public VsDataContainer getVsDataContainer() {
        return vsDataContainer;
    }

    @XmlElement(name = "vsDataContainer", namespace = "http://www.3gpp.org/ftp/specs/archive/32_series/32.625#genericNrm")
    public void setVsDataContainer(VsDataContainer vsDataContainer) {
        this.vsDataContainer = vsDataContainer;
    }
}
