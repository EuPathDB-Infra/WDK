package org.gusdb.wdk.controller.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.controller.actionutil.ActionUtility;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.AnswerValueBean;
import org.gusdb.wdk.model.jspwrap.StepBean;
import org.gusdb.wdk.model.jspwrap.UserBean;
import org.gusdb.wdk.model.jspwrap.WdkModelBean;
import org.gusdb.wdk.model.report.Reporter;
import org.gusdb.wdk.model.report.ReporterFactory;

/**
 * This Action is called by the ActionServlet when a download submit is made. It
 * 1) find selected fields (may be all fields in answer bean) 2) use
 * AnswerValueBean to get and format results 3) forward control to a jsp page
 * that displays the result
 */

public class GetDownloadResultAction extends Action {

    private static Logger logger = Logger.getLogger(GetDownloadResultAction.class);

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try {
            // get answer
            String stepId = request.getParameter(CConstants.WDK_STEP_ID_KEY);
            if (stepId == null) {
                stepId = (String) request.getAttribute(CConstants.WDK_STEP_ID_KEY);
            }
            if (stepId == null)
                throw new WdkUserException(
                        "no step id is given for which to download the result");

            String signature = request.getParameter("signature");
            UserBean wdkUser;
            if (signature != null && signature.length() > 0) {
                WdkModelBean wdkModel = ActionUtility.getWdkModel(servlet);
                wdkUser = wdkModel.getUserFactory().getUser(signature);
            } else {
                wdkUser = ActionUtility.getUser(servlet, request);
            }

            int histId = Integer.parseInt(stepId);
            StepBean userAnswer = wdkUser.getStep(histId);
            AnswerValueBean wdkAnswerValue = userAnswer.getAnswerValue();

            // get reporter name
            String reporterName = request.getParameter(CConstants.WDK_REPORT_FORMAT_KEY);

            // get configurations
            Map<String, String> config = new LinkedHashMap<String, String>();
            for (Object objKey : request.getParameterMap().keySet()) {
                String key = objKey.toString();
                if (key.equalsIgnoreCase(CConstants.WDK_STEP_ID_KEY)
                        || key.equalsIgnoreCase(CConstants.WDK_REPORT_FORMAT_KEY))
                    continue;
                String[] values = request.getParameterValues(key);
                if (values == null || values.length == 0) {
                    String value = request.getParameter(key);
                    config.put(key, value);
                } else {
                    StringBuffer sb = new StringBuffer();
                    for (int i = 0; i < values.length; i++) {
                        // ignore the empty value
                        String value = values[i].trim();
                        if (value.length() == 0) continue;

                        if (sb.length() > 0) sb.append(",");
                        sb.append(value);
                    }
                    config.put(key, sb.toString());
                }
            }

            // make report
            Reporter reporter = ReporterFactory.getReporter(wdkAnswerValue.getAnswerValue(), reporterName, config);

            response.setHeader("Pragma", "Public");
            response.setContentType(reporter.getHttpContentType());

            String fileName = reporter.getDownloadFileName();
            if (fileName != null) {
                response.setHeader(
                        "Content-disposition",
                        "attachment; filename="
                                + reporter.getDownloadFileName());
            }
            logger.info("content-type: " + reporter.getHttpContentType());
            logger.info("file-name: " + reporter.getDownloadFileName());

            ServletOutputStream out = response.getOutputStream();
            try {
              reporter.report(out);
            }
            catch (Exception e) {
              String logMarker = UUID.randomUUID().toString();
              logger.error("Error downloading result (marker: " + logMarker + ").", e);
              String message = "  An error occurred and no more content can be downloaded." +
                  "  To report this problem, use marker '" + logMarker + "'.";
              out.print(message);
              
            }

            return null;
        }
        catch (Exception ex) {
            logger.error("downloading failed", ex);
            throw ex;
        }
    }
}
