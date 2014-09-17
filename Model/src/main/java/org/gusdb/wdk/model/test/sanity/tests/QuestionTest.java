package org.gusdb.wdk.model.test.sanity.tests;

import java.util.Map;

import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.test.sanity.SanityTester.ElementTest;
import org.gusdb.wdk.model.test.sanity.SanityTester.Statistics;
import org.gusdb.wdk.model.test.sanity.TestResult;
import org.gusdb.wdk.model.user.User;

public class QuestionTest implements ElementTest {

  private final User _user;
  private final Question _question;
  private final ParamValuesSet _paramValuesSet;

  public QuestionTest(User user, Question question, ParamValuesSet paramValuesSet) {
    _user = user;
    _question = question;
    _paramValuesSet = paramValuesSet;
  }

  @Override
  public String getTestName() {
    return getTestName(_question);
  }
  
  public static String getTestName(Question question) {
    return "QUESTION " + question.getFullName() +
        " (query " + question.getQuery().getFullName() + ")";
  }

  @Override
  public String getCommand() {
    return "wdkSummary -model " + _question.getWdkModel().getProjectId() +
        " -question " + _question.getFullName() + " -rows 1 100" +
        " -params " + _paramValuesSet.getCmdLineString();
  }

  @Override
  public TestResult test(Statistics stats) throws Exception {
    int sanityMin = _paramValuesSet.getMinRows();
    int sanityMax = _paramValuesSet.getMaxRows();
    TestResult result = new TestResult(this);
    result.setExpected("Expect [" + sanityMin + " - " + sanityMax + "] rows" +
        ((sanityMin != 1 || sanityMax != ParamValuesSet.MAXROWS) ? "" : " (default)"));
    try {
      _question.getQuery().setIsCacheable(false);
      AnswerValue answerValue = _question.makeAnswerValue(_user,
          _paramValuesSet.getParamValues(), true, 0);
      int resultSize = answerValue.getResultSize();

      // get the summary attribute list
      Map<String, AttributeField> summary = answerValue.getSummaryAttributeFieldMap();

      // iterate through the page and try every summary attribute of each record
      for (RecordInstance record : answerValue.getRecordInstances()) {
        StringBuffer sb = new StringBuffer();
        for (String attrName : summary.keySet()) {
          sb.append(record.getAttributeValue(attrName));
          sb.append('\t');
        }
      }

      result.setReturned(resultSize + " rows returned");
      result.setPassed(resultSize >= sanityMin && resultSize <= sanityMax);
      return result;
    }
    finally {
      result.stopTimer();
      stats.questionsDuration += result.getDurationSecs();
      if (result.isPassed()) {
        stats.questionsPassed++;
      }
      else {
        stats.questionsFailed++;
      }
    }
  }
}
