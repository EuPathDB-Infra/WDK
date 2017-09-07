<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
<%@ taglib prefix="imp" tagdir="/WEB-INF/tags/imp" %>

<%--
Provides form input element for a given FilterParam.
--%>

<jsp:useBean id="idgen" class="org.gusdb.wdk.model.jspwrap.NumberUtilBean" scope="application" />

<%@ attribute name="qp"
              type="org.gusdb.wdk.model.jspwrap.FilterParamBean"
              required="true"
              description="parameter name"
%>

<%@ attribute name="layout"
              required="false"
              description="parameter name"
%>

<c:set var="qP" value="${qp}"/>
<c:set var="pNam" value="${qP.name}"/>
<c:set var="opt" value="0"/>
<c:set var="dependedParams" value="${qP.dependedParamNames}"/>
<c:if test="${dependedParams != null}">
  <c:set var="dependedParam" value="${dependedParams}" />
  <c:set var="dependentClass" value="dependentParam" />
</c:if>
<%-- Setting a variable to display the items in the parameter in a horizontal layout --%>
<c:set var="v" value=""/>
<c:if test="${layout == 'horizontal'}">
  <c:set var="v" value="style='display:inline'"/>
</c:if>

<%-- FIXME change data-name to data-display-name --%>
<%-- display the param as an advanced filter param --%>
<div class="param filter-param ${dependentClass}"
    dependson="${dependedParam}"
    name="${pNam}"
    data-is-allow-empty="${qp.isAllowEmpty}"
    data-title="${qp.prompt}"
    data-filter-data-type="${qp.filterDataTypeDisplayName}"
    data-type="filter-param"
    data-default-columns="${qp.defaultColumns}"
    data-trim-metadata-terms="${qp.trimMetadataTerms}"
    data-min-selected-count="${qp.minSelectedCount}"
    data-max-selected-count="${qp.maxSelectedCount}"
    data-data-id="filter-param-${qP.name}">
  <html:hidden property="value(${pNam})" />
  <div class="loading">
    <imp:image src="wdk/images/wizard-busy.gif"/>
  </div>
  <div class="filter-param-container"></div>
</div>

<%-- display invalid terms, if any. --%>
<c:set var="originalValues" value="${qP.originalValues}" />
<c:set var="invalid" value="${false}" />
<c:forEach items="${originalValues}" var="entry">
  <c:if test="${entry.value == false}">
    <c:set var="invalid" value="${true}" />
  </c:if>
</c:forEach>

<c:if test="${invalid}">
  <div class="invalid-values">
    <p>Some of the option(s) you previously selected are no longer available.</p>
    <p>Here is a list of the values you selected (unavailable options are marked in red):</p>
    <ul>
      <c:forEach items="${originalValues}" var="entry">
        <c:set var="style">
          <c:if test="${entry.value == false}">class="invalid"</c:if>
        </c:set>
        <li ${style}>${entry.key}</li>
      </c:forEach>
    </ul>
  </div>
</c:if>
