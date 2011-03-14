package org.gusdb.wdk.controller.wizard;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionServlet;
import org.gusdb.wdk.controller.action.ActionUtility;
import org.gusdb.wdk.controller.action.QuestionForm;
import org.gusdb.wdk.controller.action.ShowQuestionAction;
import org.gusdb.wdk.controller.action.WizardForm;
import org.gusdb.wdk.model.jspwrap.AnswerParamBean;
import org.gusdb.wdk.model.jspwrap.ParamBean;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.StepBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.gusdb.wdk.model.WdkUserException;

public class ShowQuestionStageHandler implements StageHandler {

    private static final String PARAM_QUESTION_NAME = "questionFullName";

    private static final String ATTR_QUESTION = "question";
    private static final String ATTR_ALLOW_BOOLEAN = "allowBoolean";

    private static final Logger logger = Logger
            .getLogger(ShowQuestionStageHandler.class);

    public Map<String, Object> execute(ActionServlet servlet,
            HttpServletRequest request, HttpServletResponse response,
            WizardForm wizardForm) throws Exception {
        logger.debug("Entering ShowQuestionStageHandler....");

        WdkModelBean wdkModel = ActionUtility.getWdkModel(servlet);
        String questionName = request.getParameter(PARAM_QUESTION_NAME);
        if (questionName == null || questionName.length() == 0)
            throw new WdkUserException("Required param " + PARAM_QUESTION_NAME
                    + " is missing.");

        QuestionBean question = wdkModel.getQuestion(questionName);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ATTR_QUESTION, question);

        if (wizardForm.getAction().equals(WizardForm.ACTION_REVISE)) {
            StepBean currentStep = StageHandlerUtility.getCurrentStep(request);
            if (currentStep.getChildStep() != null) {
                attributes.put("customName", currentStep.getChildStep()
                        .getBaseCustomName());
            } else {
                attributes.put("customName", currentStep.getBaseCustomName());
            }
        }

        // get previous step
        StepBean previousStep = StageHandlerUtility.getPreviousStep(servlet,
                request, wizardForm);

        logger.debug("previous step: " + previousStep);

        // set the previous step value
        String paramName = null;
        for (ParamBean param : question.getParams()) {
            // the previous step should be stored in the value of first
            // answer param.
            if (param instanceof AnswerParamBean) {
                paramName = param.getName();
                break;
            }
        }
        if (paramName != null) {
            // if the question is changed from a normal question to a transform,
            // and it's the first step, then revise will use the current
            // question as input of the new transform.
            StepBean inputStep = previousStep;
            if (inputStep == null)
                inputStep = StageHandlerUtility.getCurrentStep(request);

            int inputStepId = inputStep.getStepId();
            // the name here is hard-coded, it will be used by
            // ShowQuestionAction.
            request.setAttribute(ShowQuestionAction.PARAM_INPUT_STEP,
                    Integer.toString(inputStepId));
        }

        // prepare question form
        logger.debug("Preparing form for question: " + questionName);
        QuestionForm questionForm = new QuestionForm();
        ShowQuestionAction.prepareQuestionForm(question, servlet, request,
                questionForm);
        wizardForm.copyFrom(questionForm);
        logger.debug("wizard form: " + wizardForm);

        // check if boolean is allowed
        String importType = question.getRecordClass().getFullName();
        boolean allowBoolean = true;
        if (previousStep != null)
            allowBoolean = importType.equals(previousStep.getType());
        logger.debug("allow boolean: " + allowBoolean);
        attributes.put(ATTR_ALLOW_BOOLEAN, allowBoolean);

        // check the custom form
        ShowQuestionAction.checkCustomForm(servlet, request, question);

        logger.debug("Leaving ShowQuestionStageHandler....");
        return attributes;
    }
}
