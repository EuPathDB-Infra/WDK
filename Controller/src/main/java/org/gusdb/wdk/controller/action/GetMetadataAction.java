package org.gusdb.wdk.controller.action;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.gusdb.wdk.controller.actionutil.ActionUtility;
import org.gusdb.wdk.controller.actionutil.QuestionRequestParams;
import org.gusdb.wdk.controller.form.QuestionForm;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.FilterParamBean;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.gusdb.wdk.model.query.param.RequestParams;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetMetadataAction extends GetVocabAction {

  private static final String PARAM_PROPERTY = "property";

  private static final Logger logger = Logger.getLogger(GetMetadataAction.class);

  /*
   * (non-Javadoc)
   * 
   * @seeorg.apache.struts.action.Action#execute(org.apache.struts.action. ActionMapping,
   * org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest,
   * javax.servlet.http.HttpServletResponse)
   */
  @Override
  public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    logger.trace("Entering GetMetadataAction...");

    String property = request.getParameter(PARAM_PROPERTY);
    if (property == null || property.length() == 0)
      throw new WdkUserException("Required 'property' param is missing");

    WdkModelBean wdkModel = ActionUtility.getWdkModel(servlet);
    try {
      QuestionBean question = getQuestion(request, wdkModel);
      FilterParamBean param = (FilterParamBean) getParam(request, question);

      // TODO -- the following steps are to make sure the param is initialized properly;
      //         need further investigation to remove this need, and to improve performance.
      UserBean user = ActionUtility.getUser(servlet, request);
      QuestionForm qForm = (QuestionForm) form;
      RequestParams requestParams = new QuestionRequestParams(request, qForm);
      param.prepareDisplay(user, requestParams);
      param.getJsonValues();


      Map<String, List<String>> metadata = param.getMetadata(property);
      JSONArray jsMetadata = new JSONArray();
      for (String sample : metadata.keySet()) {
        JSONObject jsSample = new JSONObject();
        jsSample.put("sample", sample);
        jsSample.put("values", metadata.get(sample));
        jsMetadata.put(jsSample);
      }
      
      response.setContentType("application/json");
      PrintWriter writer = response.getWriter();
      writer.print(jsMetadata.toString());
      writer.flush();
      return null;
    }
    catch (Exception ex) {
      ex.printStackTrace();
      throw ex;
    }
  }
}
