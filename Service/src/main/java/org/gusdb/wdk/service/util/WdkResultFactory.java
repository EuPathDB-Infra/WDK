package org.gusdb.wdk.service.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.beans.FilterValue;
import org.gusdb.wdk.beans.ParamValue;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.AnswerValueBean;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.service.util.WdkResultRequestSpecifics.SortItem;

public class WdkResultFactory {

  private final UserBean _user;

  public WdkResultFactory(UserBean user) {
    _user = user;
  }

  public AnswerValueBean createResult(WdkResultRequest request, WdkResultRequestSpecifics specifics) throws WdkModelException {
    try {
      Question question = request.getQuestion();
      // FIXME: for now just past name of first filter if one exists
      List<FilterValue> filters = request.getFilterValues();
      String filter = (filters.isEmpty() ? null : filters.iterator().next().getName());
      int startIndex = specifics.getOffset();
      int endIndex = startIndex + specifics.getNumRecords();
      return new QuestionBean(question).makeAnswerValue(_user, convertParams(request.getParamValues()), startIndex, endIndex,
          convertSorting(specifics.getSorting()), filter, true, 0);
    }
    catch (WdkUserException e) {
      throw new WdkModelException(e);
    }
  }

  public AnswerValueBean createResult(WdkResultRequest request) throws WdkModelException {
    try {
      Question question = request.getQuestion();
      return new QuestionBean(question).makeAnswerValue(_user, convertParams(request.getParamValues()), true, 0);
    }
    catch (WdkUserException e) {
      throw new WdkModelException(e);
    }
  }

  private Map<String, String> convertParams(Map<String, ParamValue> params) {
    Map<String, String> conversion = new HashMap<>();
    for (ParamValue param : params.values()) {
      conversion.put(param.getName(), param.getObjectValue().toString());
    }
    return conversion;
  }
  
  private Map<String, Boolean> convertSorting(List<SortItem> sorting) {
    Map<String, Boolean> conversion = new LinkedHashMap<>();
    for (SortItem sort : sorting) {
      conversion.put(sort.getColumn().getName(), sort.getDirection().getBoolValue());
    }
    return conversion;
  }
}
