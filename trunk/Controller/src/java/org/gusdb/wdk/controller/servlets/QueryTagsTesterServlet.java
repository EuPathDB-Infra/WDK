package org.gusdb.gus.wdk.controller.servlets;

import org.gusdb.gus.wdk.model.Column;
import org.gusdb.gus.wdk.model.Param;
import org.gusdb.gus.wdk.model.Query;
import org.gusdb.gus.wdk.model.QueryInstance;
import org.gusdb.gus.wdk.model.QueryParamsException;
import org.gusdb.gus.wdk.model.QuerySet;
import org.gusdb.gus.wdk.model.RecordList;
import org.gusdb.gus.wdk.model.ResultList;
import org.gusdb.gus.wdk.model.WdkModel;
import org.gusdb.gus.wdk.model.implementation.SqlQueryInstance;
import org.gusdb.gus.wdk.view.QueryRecordGroupMgr;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * QueryTagsTesterServlet
 *
 * This servlet interacts with the query custom tags to present and validate a form
 * for the user
 *
 * Created: May 9, 2004
 *
 * @author Adrian Tivey
 * @version $Revision$ $Date$ $Author$
 */
public class QueryTagsTesterServlet extends HttpServlet {

	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		String fromPage = req.getParameter("fromPage");
		String queryRecordGroup = req.getParameter("recordGroup");
		String queryRecordName = req.getParameter("queryRecordName");
		String formName = req.getParameter("formName");
		String defaultChoice = req.getParameter("defaultChoice");
        String initialExpansion = req.getParameter("initialExpansion");
        
		if (fromPage == null) {
			msg("fromPage shouldn't be null. Internal error", res);
			return;
		}
		if (queryRecordGroup == null) {
			msg("queryRecordGroup shouldn't be null. Internal error", res);
			return;
		}
		if (queryRecordName == null) {
			msg("queryRecordName shouldn't be null. Internal error", res);
			return;
		}
		if (formName == null) {
			msg("formName shouldn't be null. Internal error", res);
			return;
		}
		if (defaultChoice == null) {
			msg("defaultChoice shouldn't be null. Internal error", res);
			return;
		}
		
        if (queryRecordName.equals(defaultChoice)) {
            req.setAttribute(formName+".error.query.noQuery", "Please choose a query");
            redirect(req, res, fromPage);
            return;
        }
        
        if (queryRecordName.indexOf('.')==-1) {
            msg("queryRecord name isn't qualified: "+queryRecordName, res);
            return;
		}
		
		// We have a queryRecord name
        WdkModel wm = (WdkModel) getServletContext().getAttribute("wdk.wdkModel");
        
        RecordList queryRecord = QueryRecordGroupMgr.getRecordList(wm, queryRecordName);
        Query sq = queryRecord.getQuery();

        if (sq == null) {
            msg("sq is null for "+queryRecordName, res);
            return;
        }
		QueryInstance sqii = sq.makeInstance();
		Map paramValues = new HashMap();
		
		req.setAttribute(formName+".sqii", sqii);
        
        boolean problem = false;
        if ("true".equals(initialExpansion)) {
            problem = true;
        } else {
            // Now check state of params
            Param[] params = sq.getParams();

            for (int i = 0; i < params.length; i++) {
                Param param = params[i];
                String paramName = param.getName();
                String passedIn = req.getParameter(formName+"."+queryRecordName+"."+paramName);
                String error = param.validateValue(passedIn);
                if ( error == null) {
                    paramValues.put(paramName, passedIn);
                } else {
                    problem = true;
                    req.setAttribute(formName+".error."+queryRecordName+"."+paramName, error);	
                }   
            }
        }
		
		if (problem) {
			// If fail, redirect to page
			redirect(req, res, fromPage);
			return;
		}
        
		// If OK, show success msg
		msg("OK, we've got a valid query. Hooray", res);
//		ResultList rl = null;
//		StringBuffer sb = new StringBuffer();
//		try {
//		    
//		    Query pageQuery = queryRecord.getRecord().makeInstance();   getQuery("RNAListInDetail");
//		    
//		    sqii.setIsCacheable(true);
//		    sqii.setValues(paramValues);
//		    
//		    SqlQueryInstance ssqi = (SqlQueryInstance) sqii;
//		    
//		    String initialResultTable = ssqi.getResultAsTable();
//		    Map values = new HashMap(3);
//		    values.put("resultTable", initialResultTable);
//		    values.put("startRow", "1");
//		    values.put("endRow", "200");
//		    SqlQueryInstance pageInstance = 
//		        (SqlQueryInstance)pageQuery.makeInstance();
//		    // pageInstance.setIsCacheable(getIsCacheable());
//		    pageInstance.setValues(values);
//		    rl = pageInstance.getResult();
//            
//		    //				ResultSet rs = sqii.getResult();
//		    
//		    if (rl == null) {
//		        sb.append("No result set returned");   
//		    } else {
//		        // Get result set meta data
//		        sb.append("<table width=\"100%\"><tr>");
//                Column[] columns = rl.getInstance().getQuery().getColumns();
//                String[] columnNames = new String[columns.length];
//                for (int i = 0; i < columns.length; i++) {
//                    columnNames[i] = columns[i].getDisplayName();
//                    System.err.println("columnNames["+i+"] is "+columnNames[i]);
////                    sb.append("<th align=\"center\"><b>&nbsp;</b></th>");
//                    sb.append("<th align=\"center\"><b>");
//                    sb.append(columnNames[i]);
//                    sb.append("</b></th>");
//                }
//                System.err.println("The number of columns is "+columnNames.length);
//		        sb.append("</tr>");
//		        while (rl.next()) {
//		            sb.append("<tr>");
//		            sb.append("<td align=\"center\"><a href=\"");
//		            sb.append(req.getContextPath());
//		            sb.append("/RecordTester");
//		            sb.append("?style=jsp&recordSetName=RNARecords&recordName=PSUCDSRecordId&primaryKey=");
//		            sb.append(rl.getValue(columnNames[0])+"&objectType="+rl.getValue(columnNames[1])+"\" >");
//		            sb.append("More details</a></td>");
//		            sb.append("<td align=\"center\">"+rl.getValue(columnNames[3])+"</td>");
//		            sb.append("<td align=\"center\"><i>"+rl.getValue(columnNames[4])+"</i></td>");
//		            sb.append("</tr>");
//		        }
//		        sb.append("<table>");
//		    }
//		} catch (SQLException e) {
//		    sb = new StringBuffer(e.toString());
//		} catch (QueryParamsException e) {
//		    sb = new StringBuffer(e.toString());
//		} catch (Exception e) {
//            e.printStackTrace();
//		    sb = new StringBuffer(e.toString());
//		}
//        finally {
//            if (rl != null) {
//                // TODO - close should probably throw RuntimeException or not at all
//                try {
//                    rl.close();
//                } catch (Exception exp) {
//                    // Deliberately empty
//                }
//            }
//        }
//		msg(sb.toString(), res);
		return;
	}

    private void msg(String msg, HttpServletResponse res) throws IOException {
        res.setContentType("text/html");
        PrintWriter out = res.getWriter();
        out.println("<html><body bkground=\"white\">"+msg+"</body></html>" );
    }
    
    private void redirect(HttpServletRequest req, HttpServletResponse res, String page) throws ServletException, IOException {
        ServletContext sc = getServletContext();
        RequestDispatcher rd = sc.getRequestDispatcher(page);
        rd.forward(req, res);
    }
    
}