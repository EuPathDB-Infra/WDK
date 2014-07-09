/**
 * 
 */
package org.gusdb.wdk.model.report;

import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.json.JSONException;

/**
 * @author xingao
 * 
 */
public abstract class Reporter implements Iterable<AnswerValue> {

    public static final String FIELD_FORMAT = "downloadType";
    public static final String PROPERTY_PAGE_SIZE = "page_size";

    private static final int SORTING_THRESHOLD = 100;

    private final static Logger logger = Logger.getLogger(Reporter.class);

    protected class PageAnswerIterator implements Iterator<AnswerValue> {

        private AnswerValue baseAnswer;
        private int endIndex;
        private int startIndex;
        private int maxPageSize;
        private int resultSize;

        public PageAnswerIterator(AnswerValue answerValue, int startIndex,
                int endIndex, int maxPageSize) throws WdkModelException, WdkUserException {
            this.baseAnswer = answerValue;

            // determine the end index, which should be no bigger result size,
            // since the index starts from 1
            resultSize = baseAnswer.getResultSize();
            this.endIndex = Math.min(endIndex, resultSize);
            this.startIndex = startIndex;
            this.maxPageSize = maxPageSize;
        }

        @Override
        public boolean hasNext() {
            // if the current
            return (startIndex <= endIndex);
        }

        @Override
        public AnswerValue next() {
            // decide the new end index for the page answer
            int pageEndIndex = Math.min(endIndex, startIndex + maxPageSize - 1);

            logger.debug("Getting records #" + startIndex + " to #"
                    + pageEndIndex);

            AnswerValue answerValue = new AnswerValue(baseAnswer, startIndex,
                    pageEndIndex);

            // disable sorting if the total size is bigger than threshold
            if (resultSize > SORTING_THRESHOLD)
                answerValue.setSortingMap(new LinkedHashMap<String, Boolean>());

            // update the current index
            startIndex = pageEndIndex + 1;
            return answerValue;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("This functionality is not implemented.");
        }

    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public abstract String getConfigInfo();

    public String getPropertyInfo() {
        StringBuffer propInfo = new StringBuffer();
        for (String propName : properties.keySet()) {
            propInfo.append(propName + ": " + properties.get(propName));
            propInfo.append(System.getProperty("line.separator"));
        }
        return propInfo.toString();
    }

    protected abstract void write(OutputStream out) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException;

    protected Map<String, String> properties;
    protected Map<String, String> config;

    protected WdkModel wdkModel;

    protected AnswerValue baseAnswer;
    private int startIndex;
    private int endIndex;

    protected String format = "plain";
    private String description = null;
    protected int maxPageSize = 100;

    protected Reporter(AnswerValue answerValue, int startIndex, int endIndex) {
        this.baseAnswer = answerValue;
        this.startIndex = startIndex;
        this.endIndex = endIndex;

        config = new LinkedHashMap<String, String>();
    }

    /**
	 * @throws WdkModelException if error while setting properties on reporter 
	 */
    public void setProperties(Map<String, String> properties) throws WdkModelException {
        this.properties = properties;
        if (properties.containsKey(PROPERTY_PAGE_SIZE))
            maxPageSize = Integer.valueOf(properties.get(PROPERTY_PAGE_SIZE));
    }

    public int getResultSize() throws WdkModelException, WdkUserException {
        return this.baseAnswer.getResultSize();
    }

    public AnswerValue getAnswerValue() {
        return this.baseAnswer;
    }

    public void configure(Map<String, String> config) {
        if (config != null) {
            this.config = config;

            if (config.containsKey(FIELD_FORMAT)) {
                format = config.get(FIELD_FORMAT);
            }
            if (config.containsKey(PROPERTY_PAGE_SIZE))
              maxPageSize = Integer.valueOf(config.get(PROPERTY_PAGE_SIZE));
        }
    }

    /**
     * Hook used to perform any setup needed before calling the write method.
     * 
     * @throws WdkModelException if error while initializing reporter
     */
    protected abstract void initialize() throws WdkModelException;

    /**
     * Hook used to perform any teardown needed after calling the write method.
     */
    protected abstract void complete();

    public void setWdkModel(WdkModel wdkModel) {
        this.wdkModel = wdkModel;
    }

    public String getHttpContentType() {
        // by default, generate result in plain text format
        return "text/plain";
    }

    public String getDownloadFileName() {
        // by default, display the result in the browser, by seting the file
        // name as null
        return null;
    }

    // =========================================================================
    // provide the wrapper methods to answer object, in order not to expose the
    // answer itself to avoid accidental changes on the base answer. The record
    // access to the answer should be through the page answer iterator
    // =========================================================================

    /**
     * @return get the questions of the answer
     */
    protected Question getQuestion() {
        return baseAnswer.getQuestion();
    }

    protected Map<String, AttributeField> getSummaryAttributes() throws WdkModelException {
        return baseAnswer.getSummaryAttributeFieldMap();
    }

    @Override
    public Iterator<AnswerValue> iterator() {
        try {
            return new PageAnswerIterator(baseAnswer, startIndex, endIndex,
                    maxPageSize);
        } catch (WdkModelException | WdkUserException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void report(OutputStream out) throws SQLException,
            WdkModelException, NoSuchAlgorithmException, WdkUserException,
            JSONException {
        initialize();
        try {
            // write header
            write(out);
        } finally {
            complete();
        }
    }
}
