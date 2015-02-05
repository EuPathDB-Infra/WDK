<%-- 
Provides form input element for a given StringParam.

--%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bean" uri="http://jakarta.apache.org/struts/tags-bean" %>
<%@ taglib prefix="html" uri="http://jakarta.apache.org/struts/tags-html" %>

<%@ attribute name="qp"
              type="org.gusdb.wdk.model.jspwrap.StringParamBean"
              required="true"
              description="parameter name"
%>

<c:set var="qP" value="${qp}"/>
<c:set var="pNam" value="${qP.name}"/>
<c:set var="length" value="${qP.length}"/>

<div class="param stringParam" name="${pNam}">
<%--
  <c:if test="${not empty qP.visibleHelp}">
    <p style="margin-top:3px">${qP.visibleHelp}</p>
  </c:if>
--%>

<c:choose>
  <c:when test="${qP.isVisible == false}">
    <html:hidden property="value(${pNam})"/>
  </c:when>
  <c:when test="${qP.isReadonly}">
    <html:text styleId="${pNam}" property="value(${pNam})" size="35" readonly="true" />
  </c:when>
  <c:when test="${length > 50}">
    <html:textarea styleId="${pNam}" property="value(${pNam})" cols="35" rows="10" />
  </c:when>
  <c:otherwise>
    <html:text styleId="${pNam}" property="value(${pNam})" size="35" />
  </c:otherwise>
</c:choose>

</div>

