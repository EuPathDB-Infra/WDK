package org.gusdb.wdk.controller.action;

import java.io.File;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.gusdb.wdk.controller.ApplicationInitListener;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.model.jspwrap.EnumParamBean;
import org.gusdb.wdk.model.jspwrap.ParamBean;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.QuestionSetBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;

/**
 * This Action is called by the ActionServlet when a flat display of
 * QuestionSets is needed. It 1) gets all questions in all questionSets from the
 * WDK model 2) forwards control to a jsp page that displays all questions in
 * all questionSets
 */

public class ShowQuestionSetsFlatAction extends ShowQuestionSetsAction {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        QuestionSetForm qSetForm = (QuestionSetForm) form;
        prepareQuestionSetForm(getServlet(), qSetForm);

        ServletContext svltCtx = getServlet().getServletContext();
        String customViewDir = CConstants.WDK_CUSTOM_VIEW_DIR
	    + File.separator + CConstants.WDK_PAGES_DIR;
        String customViewFile = customViewDir + File.separator
                + CConstants.WDK_CUSTOM_QUESTIONSETS_FLAT_PAGE;

        ActionForward forward = null;
        if (ApplicationInitListener.resourceExists(customViewFile, svltCtx)) {
            forward = new ActionForward(customViewFile);
        } else {
            forward = mapping.findForward(CConstants.SHOW_QUESTIONSETSFLAT_MAPKEY);
        }

        sessionStart(request, getServlet());

        return forward;
    }

    protected void prepareQuestionSetForm(ActionServlet servlet,
            QuestionSetForm qSetForm) throws Exception {
        // ServletContext context = servlet.getServletContext();

        WdkModelBean wdkModel = (WdkModelBean) getServlet().getServletContext().getAttribute(
                CConstants.WDK_MODEL_KEY);

        String qFullName = qSetForm.getQuestionFullName();
        Map<String, QuestionSetBean> qSetMap = wdkModel.getQuestionSetsMap();
        for (String qSetName : qSetMap.keySet()) {
            if (qFullName != null && !qFullName.startsWith(qSetName)) continue;

            QuestionSetBean wdkQuestionSet = qSetMap.get(qSetName);

            Map<String, QuestionBean> questionMap = wdkQuestionSet.getQuestionsMap();
            for (String qName : questionMap.keySet()) {
                QuestionBean wdkQuestion = questionMap.get(qName);

                // skip the unused questions
                if (qFullName != null
                        && !wdkQuestion.getFullName().equals(qFullName))
                    continue;

                ParamBean[] params = wdkQuestion.getParams();

                for (int i = 0; i < params.length; i++) {
                    ParamBean p = params[i];
                    String key = qSetName + "_" + qName + "_" + p.getName();
                    if (p instanceof EnumParamBean) {
                        // not assuming fixed order, so call once, use twice.
                        String[] flatVocab = ((EnumParamBean) p).getVocab();
                        String[] labels = ((EnumParamBean) p).getDisplays();
                        qSetForm.getMyValues().put(p.getName(), flatVocab);
                        qSetForm.getMyLabels().put(
                                p.getName(),
                                labels);
                    }
                    qSetForm.getMyProps().put(key, p.getDefault());
                }
            }
        }
    }
}
