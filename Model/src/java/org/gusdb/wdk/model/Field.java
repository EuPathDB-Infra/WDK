/**
 * 
 */
package org.gusdb.wdk.model;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import org.json.JSONException;

/**
 * @author Jerric
 * @created Jan 17, 2006
 */
public abstract class Field extends WdkModelBase {

    protected String name;
    protected String displayName;
    protected String help;
    protected String type;
    protected int truncateTo;
    protected boolean internal;
    protected boolean inReportMaker;

    protected RecordClass recordClass;

    /**
     * 
     */
    public Field() {
        // initialize the optional properties
        internal = false;
        inReportMaker = true;
        truncateTo = Utilities.TRUNCATE_DEFAULT;
    }

    /**
     * @return Returns the displayName.
     */
    public String getDisplayName() {
        return (displayName == null) ? name : displayName;
    }

    /**
     * @param displayName
     *            The displayName to set.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return Returns the help.
     */
    public String getHelp() {
        return this.help;
    }

    /**
     * @param help
     *            The help to set.
     */
    public void setHelp(String help) {
        this.help = help;
    }

    /**
     * @return Returns the inReportMaker.
     */
    public boolean isInReportMaker() {
        return this.inReportMaker;
    }

    /**
     * @param inReportMaker
     *            The inReportMaker to set.
     */
    public void setInReportMaker(boolean inReportMaker) {
        this.inReportMaker = inReportMaker;
    }

    /**
     * @return Returns the internal.
     */
    public boolean isInternal() {
        return this.internal;
    }

    /**
     * @param internal
     *            The internal to set.
     */
    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @param name
     *            The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return Returns the truncateTo.
     */
    public int getTruncateTo() {
        return this.truncateTo;
    }

    /**
     * @param truncateTo
     *            The truncateTo to set.
     */
    public void setTruncateTo(int truncateTo) {
        this.truncateTo = truncateTo;
    }

    /**
     * @return Returns the type.
     */
    public String getType() {
        return this.type;
    }

    /**
     * @param type
     *            The type to set.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the recordClass
     */
    public RecordClass getRecordClass() {
        return recordClass;
    }

    /**
     * @param recordClass
     *            the recordClass to set
     */
    public void setRecordClass(RecordClass recordClass) {
        this.recordClass = recordClass;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getDisplayName();
    }
}
