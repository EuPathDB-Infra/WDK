package org.gusdb.wdk.model.report;

import java.util.Map;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.stream.PagedAnswerRecordStream;
import org.gusdb.wdk.model.answer.stream.RecordStream;

/**
 * A PagedReporter outputs a result in a paged fashion in order to avoid memory problems.  To do so, it
 * iterates over a series of AnswerValues it generates, each of which represents a page of records.
 * 
 * The default page size is 100 records.
 * 
 * @author rdoherty
 */
public abstract class PagedReporter extends AbstractReporter {

  public static final String PROPERTY_PAGE_SIZE = "page_size";

  private static final int DEFAULT_PAGE_SIZE = 100;

  protected int _pageSize = DEFAULT_PAGE_SIZE;

  protected PagedReporter(AnswerValue answerValue) {
    super(answerValue);
  }

  @Override
  public void setProperties(Map<String, String> properties) throws WdkModelException {
    super.setProperties(properties);
    if (properties.containsKey(PROPERTY_PAGE_SIZE)) {
      try {
        _pageSize = Integer.valueOf(properties.get(PROPERTY_PAGE_SIZE));
      }
      catch (NumberFormatException e) {
        throw new WdkModelException("Reporter property '" + PROPERTY_PAGE_SIZE + "' must be a positive integer.");
      }
    }
  }

  public RecordStream getRecords() {
    return new PagedAnswerRecordStream(_baseAnswer, _pageSize);
  }

  @Override
  public String getHelp() {
    return super.getHelp() + "Optional property: '" + PROPERTY_PAGE_SIZE +
        "': specifies page size, defaults to " + DEFAULT_PAGE_SIZE;
  }

}
