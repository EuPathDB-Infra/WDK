package org.gusdb.wdk.controller.action.analysis;

import org.apache.log4j.Logger;
import org.gusdb.wdk.controller.actionutil.ActionResult;
import org.gusdb.wdk.controller.actionutil.ParamGroup;
import org.gusdb.wdk.model.user.analysis.StepAnalysisContext;

public class ShowStepAnalysisFormAction extends AbstractStepAnalysisIdAction {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(ShowStepAnalysisFormAction.class);
  
  @Override
  protected ActionResult handleRequest(ParamGroup params) throws Exception {

    StepAnalysisContext context = getContextFromPassedId();
    
    if (!context.getIsValidStep()) {
      return new ActionResult().setViewName("invalidStep")
          .setRequestAttribute("reason", context.getInvalidStepReason());
    }
    
    String resolvedView = getAnalysisMgr().getViewResolver().resolveFormView(this, context);
    Object formViewModel = getAnalysisMgr().getFormViewModel(context);
    return new ActionResult().setViewPath(resolvedView)
        .setRequestAttribute("wdkModel", getWdkModel())
        .setRequestAttribute("viewModel", formViewModel);
    
    // special case for interacting with a WDK question
    /*
    if (analyzer instanceof AbstractWdkQuestionAnalyzer) {
      AbstractWdkQuestionAnalyzer questionAnalyzer = (AbstractWdkQuestionAnalyzer)analyzer;
      
      for (Entry<String,Object> entry : questionAnalyzer.getQuestionViewModel(getWdkModel().getModel()).entrySet()) {
        result.setRequestAttribute(entry.getKey(), entry.getValue());
      }
    }*/
  }

  /*
  public void assign(ActionForm form,
      HttpServletRequest request, HttpServletResponse response)
      throws Exception {

    ActionServlet servlet = getServlet();
    QuestionForm qForm = (QuestionForm) getStrutsActionForm();
    String qFullName = getQuestionName(qForm, request);
    ActionUtility.getWdkModel(servlet).validateQuestionFullName(qFullName);
    QuestionBean wdkQuestion = getQuestionBean(servlet, qFullName);

    prepareQuestionForm(wdkQuestion, servlet, request, qForm);
    setParametersAsAttributes(request);
  */
}
