package org.gusdb.wdk.model;

import java.util.logging.Logger;

public class AttributeValue {

    private static final Logger logger = WdkLogManager.getLogger("org.gusdb.wdk.model.Attribute");
    
    String name;
    RecordClass recordClass;
    Object value;

    /**
     * @param value may be null to indicate this is a valueless attribute value.
     * This is used so that the attribute can be described in a context when
     * no values have been provided yet.
     */
    public AttributeValue(RecordClass recordClass, String attributeName, Object value) {
	this.recordClass = recordClass;
	this.name = attributeName;
	this.value = value;
    } 

    public String getName() {
        return name;
    }

    public String getSpecialType() {
        return recordClass.getAttributeSpecialType(name);
    }

    public String getHelp() {
        return "no help yet";
    }

    public String getDisplayName() {
        return recordClass.getDisplayName(name);
    }

    public Object getValue() {
	return value;
    }

    public String toString() {
       String newline = System.getProperty( "line.separator" );
       String classnm = this.getClass().getName();
       StringBuffer buf = 
	   new StringBuffer(classnm + ": name='" + name + "'" + newline +
			    "  displayName='" + getDisplayName() + "'" + newline +
			    "  help='" + getHelp() + "'" + newline +
			    "  specialType='" + getSpecialType() + "'" + newline 
			    );

       return buf.toString();
	
    }
    
}

