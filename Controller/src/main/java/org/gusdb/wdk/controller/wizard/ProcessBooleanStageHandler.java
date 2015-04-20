package org.gusdb.wdk.controller.wizard;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionServlet;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.controller.action.ProcessBooleanAction;
import org.gusdb.wdk.controller.action.ProcessQuestionAction;
import org.gusdb.wdk.controller.actionutil.ActionUtility;
import org.gusdb.wdk.controller.form.QuestionForm;
import org.gusdb.wdk.controller.form.WizardForm;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.StepBean;
import org.gusdb.wdk.model.jspwrap.StrategyBean;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;

public class ProcessBooleanStageHandler implements StageHandler {

  public static final String PARAM_QUESTION = "questionFullName";
  public static final String PARAM_CUSTOM_NAME = "customName";
  public static final String PARAM_STRATEGY = "strategy";
  public static final String PARAM_IMPORT_STRATEGY = "importStrategy";

  public static final String ATTR_IMPORT_STEP = ProcessBooleanAction.PARAM_IMPORT_STEP;

  private static final Logger logger = Logger.getLogger(ProcessBooleanStageHandler.class);

  @Override
  public Map<String, Object> execute(ActionServlet servlet, HttpServletRequest request,
      HttpServletResponse response, WizardForm wizardForm) throws Exception {
    logger.debug("Entering BooleanStageHandler...");

    UserBean user = ActionUtility.getUser(servlet, request);
    WdkModelBean wdkModel = ActionUtility.getWdkModel(servlet);

    StepBean childStep = null;
    
    String strStratId = request.getParameter(PARAM_STRATEGY);
    StrategyBean strategy = null;
    if (strStratId != null && strStratId.length() > 0) {
      int strategyId = Integer.valueOf(strStratId.split("_", 2)[0]);
      strategy = user.getStrategy(strategyId);
    }

    // unify between question and strategy
    String questionName = request.getParameter(PARAM_QUESTION);
    String importStrategyId = request.getParameter(PARAM_IMPORT_STRATEGY);
    if (questionName != null && questionName.length() > 0) {
      // a question name specified, either create a step from it, or revise a current step
      String action = request.getParameter(ProcessBooleanAction.PARAM_ACTION);
      if (action.equals(WizardForm.ACTION_REVISE)) {
        childStep = updateStepWithQuestion(servlet, request, wizardForm, questionName, user, wdkModel);
      }
      else {
        childStep = createStepFromQuestion(servlet, request, wizardForm, strategy, questionName, user, wdkModel);
      }
    }
    else if (importStrategyId != null && importStrategyId.length() > 0) {
      // a step specified, it must come from an insert strategy. make a
      // copy of it, and mark it as collapsable.
      childStep = createStepFromStrategy(user, strategy, Integer.valueOf(importStrategyId));
    }

    String customName = request.getParameter(PARAM_CUSTOM_NAME);
    if (customName != null && customName.trim().length() > 0) {
      childStep.setCustomName(customName);
      childStep.update(false);
    }

    Map<String, Object> attributes = new HashMap<String, Object>();
    // the childStep might not be created, in which case user just revises
    // the boolean operator.
    logger.debug("child step: " + childStep);
    if (childStep != null) {
      attributes.put(ATTR_IMPORT_STEP, childStep.getStepId());
    }
    return attributes;
  }

  private StepBean updateStepWithQuestion(ActionServlet servlet, HttpServletRequest request,
      WizardForm wizardForm, String questionName, UserBean user, WdkModelBean wdkModel)
      throws WdkUserException, WdkModelException {
    logger.debug("updating step with question: " + questionName);

    // get the assigned weight
    String strWeight = request.getParameter(CConstants.WDK_ASSIGNED_WEIGHT_KEY);
    int weight = Utilities.DEFAULT_WEIGHT;
    if (strWeight != null && strWeight.length() > 0) {
      if (!strWeight.matches("[\\-\\+]?\\d+"))
        throw new WdkUserException("Invalid weight value: '" + strWeight +
            "'. Only integer numbers are allowed.");
      if (strWeight.length() > 9)
        throw new WdkUserException("Weight number is too big: " + strWeight);
      weight = Integer.parseInt(strWeight);
    }

    // get params
    QuestionForm questionForm = new QuestionForm();
    questionForm.setServlet(servlet);
    questionForm.setQuestionFullName(questionName);
    questionForm.copyFrom(wizardForm);
    Map<String, String> params = ProcessQuestionAction.prepareParams(user, request, questionForm);

    // get the boolean/span step, then get child from the boolean
    String strStepId = request.getParameter(ProcessBooleanAction.PARAM_STEP);
    if (strStepId == null || strStepId.length() == 0)
      throw new WdkUserException("The required param \"" + ProcessBooleanAction.PARAM_STEP + "\" is missing.");

    StepBean booleanStep = user.getStep(Integer.valueOf(strStepId));
    StepBean childStep = booleanStep.getChildStep();

    // revise on the child step
    childStep.setQuestionName(questionName);
    childStep.setParamValues(params);
    childStep.setAssignedWeight(weight);
    childStep.saveParamFilters();
    return childStep;
  }

  private StepBean createStepFromQuestion(ActionServlet servlet, HttpServletRequest request,
      WizardForm wizardForm, StrategyBean strategy, String questionName, UserBean user, WdkModelBean wdkModel)
      throws WdkUserException, WdkModelException {
    logger.debug("creating step from question: " + questionName);

    // get the assigned weight
    String strWeight = request.getParameter(CConstants.WDK_ASSIGNED_WEIGHT_KEY);
    int weight = Utilities.DEFAULT_WEIGHT;
    if (strWeight != null && strWeight.length() > 0) {
      if (!strWeight.matches("[\\-\\+]?\\d+"))
        throw new WdkUserException("Invalid weight value: '" + strWeight +
            "'. Only integer numbers are allowed.");
      if (strWeight.length() > 9)
        throw new WdkUserException("Weight number is too big: " + strWeight);
      weight = Integer.parseInt(strWeight);
    }

    // get params
    QuestionForm questionForm = new QuestionForm();
    questionForm.setServlet(servlet);
    questionForm.setQuestionFullName(questionName);
    questionForm.copyFrom(wizardForm);
    Map<String, String> params = ProcessQuestionAction.prepareParams(user, request, questionForm);

    // create child step
    QuestionBean question = wdkModel.getQuestion(questionName);
    return user.createStep(strategy.getStrategyId(), question, params, null, false, true, weight);
  }

  private StepBean createStepFromStrategy(UserBean user, StrategyBean newStrategy, int importStrategyId) throws WdkModelException,
      WdkUserException {
    logger.debug("creating step from strategy: " + importStrategyId);
    StrategyBean importStrategy = user.getStrategy(importStrategyId);
    StepBean step = importStrategy.getLatestStep();
    StepBean childStep = step.deepClone(newStrategy.getStrategyId());
    childStep.setIsCollapsible(true);
    childStep.setCollapsedName("Copy of " + importStrategy.getName());
    childStep.update(false);
    return childStep;
  }
}
