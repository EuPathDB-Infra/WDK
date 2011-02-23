package org.gusdb.wdk.controller.action;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.gusdb.wdk.controller.ApplicationInitListener;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.AnswerParamBean;
import org.gusdb.wdk.model.jspwrap.DatasetBean;
import org.gusdb.wdk.model.jspwrap.DatasetParamBean;
import org.gusdb.wdk.model.jspwrap.EnumParamBean;
import org.gusdb.wdk.model.jspwrap.ParamBean;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.QuestionSetBean;
import org.gusdb.wdk.model.jspwrap.StepBean;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.json.JSONException;

/**
 * This Action is called by the ActionServlet when a WDK question is requested.
 * It 1) finds the full name from the form, 2) gets the question from the WDK
 * model 3) forwards control to a jsp page that displays a question form
 */

public class ShowQuestionAction extends ShowQuestionSetsFlatAction {

    private static Logger logger = Logger.getLogger(ShowQuestionAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        logger.debug("Entering ShowQuestionAction..");

        try {
            String qFullName = ((QuestionSetForm) form).getQuestionFullName();
            if (qFullName == null) {
                qFullName = request.getParameter(CConstants.QUESTION_FULLNAME_PARAM);
            }
            if (qFullName == null) {
                qFullName = (String) request.getAttribute(CConstants.QUESTION_FULLNAME_PARAM);
            }
            QuestionBean wdkQuestion = getQuestionByFullName(qFullName);
            if (wdkQuestion == null) 
                throw new WdkUserException("The question '" + qFullName
                        + "' doesn't exist.");

            QuestionForm qForm = prepareQuestionForm(wdkQuestion, request,
                    (QuestionForm) form);

            QuestionSetForm qSetForm = (QuestionSetForm) request.getAttribute(CConstants.QUESTIONSETFORM_KEY);
            if (null == qSetForm) {
                qSetForm = new QuestionSetForm();
                request.setAttribute(CConstants.QUESTIONSETFORM_KEY, qSetForm);
            }
            qSetForm.setQuestionFullName(qFullName);
            prepareQuestionSetForm(getServlet(), qSetForm);

            ServletContext svltCtx = getServlet().getServletContext();

            boolean partial = Boolean.valueOf(request.getParameter("partial"));

            String defaultViewFile;
            if (partial) {
                defaultViewFile = CConstants.WDK_DEFAULT_VIEW_DIR
                        + File.separator + CConstants.WDK_PAGES_DIR
                        + File.separator + "question.form.jsp";
            } else {
                defaultViewFile = CConstants.WDK_CUSTOM_VIEW_DIR
                        + File.separator + CConstants.WDK_PAGES_DIR
                        + File.separator + CConstants.WDK_QUESTION_PAGE;
            }

            ActionForward forward = new ActionForward(defaultViewFile);

            String fileToInclude = null;

            String baseFilePath = CConstants.WDK_CUSTOM_VIEW_DIR
                    + File.separator + CConstants.WDK_PAGES_DIR
                    + File.separator + CConstants.WDK_QUESTIONS_DIR;
            String customViewFile1 = baseFilePath + File.separator
                    + wdkQuestion.getFullName() + ".form.jsp";
            String customViewFile2 = baseFilePath + File.separator
                    + wdkQuestion.getQuestionSetName() + ".form.jsp";
            String customViewFile3 = baseFilePath + File.separator
                    + "question.form.jsp";

            if (ApplicationInitListener.resourceExists(customViewFile1, svltCtx)) {
                fileToInclude = customViewFile1;
            } else if (ApplicationInitListener.resourceExists(customViewFile2,
                    svltCtx)) {
                fileToInclude = customViewFile2;
            } else if (ApplicationInitListener.resourceExists(customViewFile3,
                    svltCtx)) {
                fileToInclude = customViewFile3;
            }

            System.out.println("Path to file: " + fileToInclude);
            request.setAttribute("customForm", fileToInclude);

            Enumeration<?> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = (String) paramNames.nextElement();
                String[] values = request.getParameterValues(paramName);
                String value = Utilities.fromArray(values);
                request.setAttribute(paramName, value);
            }

            String gotoSum = request.getParameter(CConstants.GOTO_SUMMARY_PARAM);
            if (qForm.getParamsFilled() && "1".equals(gotoSum)) {
                forward = mapping.findForward(CConstants.SKIPTO_SUMMARY_MAPKEY);
                // System.out.println("SQA: form has all param vals, go to
                // summary
                // page " + forward.getPath() + " directly");
            }

            return forward;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    protected QuestionBean getQuestionByFullName(String qFullName) {
        int dotI = qFullName.indexOf('.');
        String qSetName = qFullName.substring(0, dotI);
        String qName = qFullName.substring(dotI + 1, qFullName.length());

        WdkModelBean wdkModel = (WdkModelBean) getServlet().getServletContext().getAttribute(
                CConstants.WDK_MODEL_KEY);

        QuestionSetBean wdkQuestionSet = (QuestionSetBean) wdkModel.getQuestionSetsMap().get(
                qSetName);
        if (wdkQuestionSet == null) return null;
        QuestionBean wdkQuestion = (QuestionBean) wdkQuestionSet.getQuestionsMap().get(
                qName);
        return wdkQuestion;
    }

    protected QuestionForm prepareQuestionForm(QuestionBean wdkQuestion,
            HttpServletRequest request, QuestionForm qForm)
            throws WdkUserException, WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException {
        // get the current user
        ActionServlet servlet = getServlet();
        WdkModelBean wdkModel = (WdkModelBean) servlet.getServletContext().getAttribute(
                CConstants.WDK_MODEL_KEY);

        UserBean user = (UserBean) request.getSession().getAttribute(
                CConstants.WDK_USER_KEY);
        if (user == null) {
            user = wdkModel.getUserFactory().getGuestUser();
            request.getSession().setAttribute(CConstants.WDK_USER_KEY, user);
        }

        logger.debug("strategy count: " + user.getStrategyCount());

        qForm.setServlet(servlet);

        boolean hasAllParams = true;
        Map<String, String> paramValueMap = qForm.getMyProps();
        ParamBean[] params = wdkQuestion.getParams();
        for (ParamBean param : params) {
            param.setUser(user);
            String paramName = param.getName();
            String paramValues = paramValueMap.get(paramName);

            if (paramValues == null || paramValues.length() == 0)
                paramValues = Utilities.fromArray(request.getParameterValues(paramName));
            if (paramValues == null || paramValues.length() == 0)
                paramValues = null;

            // handle the additional information
            if (param instanceof EnumParamBean) {
                EnumParamBean enumParam = (EnumParamBean) param;
                String[] terms = enumParam.getVocab();
                String[] labels = enumParam.getDisplays();
                qForm.setMyLabels(paramName, labels);
                qForm.setMyValues(paramName, terms);

                // if no default is assigned, use the first enum item
                if (paramValues == null) {
                    String defaultValue = param.getDefault();
                    if (defaultValue != null) paramValues = defaultValue;
                } else {
                    paramValues = param.dependentValueToRawValue(user,
                            paramValues);
                }
            } else if (param instanceof AnswerParamBean) {
                if (paramValues == null) {
                    AnswerParamBean answerParam = (AnswerParamBean) param;
                    StepBean[] steps = answerParam.getSteps(user);
                    String[] terms = new String[steps.length];
                    String[] labels = new String[steps.length];
                    for (int idx = 0; idx < steps.length; idx++) {
                        StepBean step = steps[idx];
                        terms[idx] = Integer.toString(step.getStepId());
                        labels[idx] = "#" + step.getStepId() + " - "
                                + step.getCustomName();
                    }
                    qForm.setMyLabels(paramName, labels);
                    qForm.setMyValues(paramName, terms);

                    // if no step is assigned, use the first step
                    paramValues = terms[0];
                }
            } else if (param instanceof DatasetParamBean) {
                DatasetParamBean datasetParam = (DatasetParamBean) param;

                // check if the param value is assigned
                if (paramValues != null) {
                    datasetParam.setDependentValue(paramValues);
                    DatasetBean dataset = datasetParam.getDataset();
                    request.setAttribute(paramName + "_dataset", dataset);
                } else {
                    String defaultValue = param.getDefault();
                    if (defaultValue != null) paramValues = defaultValue;
                }
            } else {
                paramValues = param.dependentValueToRawValue(user, paramValues);
                if (paramValues == null) {
                    String defaultValue = param.getDefault();
                    if (defaultValue != null) paramValues = defaultValue;
                } else {
                    paramValues = param.dependentValueToRawValue(user,
                            paramValues);
                }
            }
            if (paramValues == null) hasAllParams = false;
            else qForm.setMyProp(paramName, paramValues);
            logger.debug("param: " + paramName + "='" + paramValues + "'");
        }

        qForm.setQuestion(wdkQuestion);
        qForm.setParamsFilled(hasAllParams);

        // if (request.getParameter(CConstants.VALIDATE_PARAM) == "0")
        // always ignore the validating on ShowQuestionAction
        qForm.setNonValidating();

        request.setAttribute(CConstants.QUESTIONFORM_KEY, qForm);
        request.setAttribute(CConstants.WDK_QUESTION_KEY, wdkQuestion);

        return qForm;
    }
}
