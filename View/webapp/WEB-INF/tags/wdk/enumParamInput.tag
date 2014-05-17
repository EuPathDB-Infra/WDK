<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bean" uri="http://jakarta.apache.org/struts/tags-bean" %>
<%@ taglib prefix="html" uri="http://jakarta.apache.org/struts/tags-html" %>
<%@ taglib prefix="imp" tagdir="/WEB-INF/tags/imp" %>

<%--
Provides form input element for a given EnumParam.

For a multi-selectable parameter a form element is provided as either a
series of checkboxes or a multiselect menu depending on number of
parameter options. Also, if number of options is over a threshold, this tag
includes a checkAll button to select all options for the parameter.

Otherwise a standard select menu is used.
--%>

<jsp:useBean id="idgen" class="org.gusdb.wdk.model.jspwrap.NumberUtilBean" scope="application" />

<%@ attribute name="qp"
              type="org.gusdb.wdk.model.jspwrap.EnumParamBean"
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
<c:set var="displayType" value="${qP.displayType}"/>
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

<c:choose>
  <c:when test="${qP.multiPick}">
    <%-- multiPick is true, use checkboxes or scroll pane --%>
    <c:choose>
      <c:when test="${displayType eq 'checkBox' or (displayType eq null and fn:length(qP.vocab) lt 15)}"><!-- use checkboxes -->
        <div class="param param-multiPick ${dependentClass}" dependson="${dependedParam}" name="${pNam}">
          <c:set var="initialCount" value="${fn:length(qP.currentValues)}"/>
          <imp:enumCountWarning enumParam="${qP}" initialCount="${initialCount}"/>
          <c:set var="changeCode" value="window.wdk.parameterHandlers.adjustEnumCountBoxes('${qP.name}aaa')"/>
          <c:set var="i" value="0"/>
          <table border="1" cellspacing="0"><tr><td>
            <ul>
              <c:forEach items="${qP.displayMap}" var="entity" varStatus="loop">
                <c:if test="${i == 0}"><c:set var="checked" value="checked"/></c:if>
                <li>
                  <label>
                    <html:multibox property="array(${pNam})" value="${entity.key}" styleId="${pNam}_${loop.index}" onchange="${changeCode}"/>
                    <c:choose>
                      <%-- test for param labels to italicize --%>
                      <c:when test="${pNam == 'organism' or pNam == 'ecorganism'}">
                        <i>${entity.value}</i>&nbsp;
                      </c:when>
                      <c:otherwise> <%-- use multiselect menu --%>
                        ${entity.value}&nbsp;
                      </c:otherwise>
                    </c:choose>
                    <c:set var="i" value="${i+1}"/>
                    <c:set var="checked" value=""/>
                  </label>
                </li>
              </c:forEach>
            </ul>
            &nbsp;<imp:selectAllParamOpt enumParam="${qP}" onchange="${changeCode}"/>
          </td></tr></table>
        </div>
      </c:when>
    
      <%-- use a tree list --%>
      <c:when test="${displayType eq 'treeBox'}">
        <div class="param ${dependentClass}" dependson="${dependedParam}" name="${pNam}">
          <imp:enumCountWarning enumParam="${qP}" initialCount="0"/>
          <c:set var="updateCountFunc">window.wdk.parameterHandlers.adjustEnumCountTree('${qP.name}aaa',${qP.countOnlyLeaves})</c:set>
          <imp:checkboxTree id="${pNam}CBT${idgen.nextId}" rootNode="${qP.paramTree}" checkboxName="array(${pNam})"
              buttonAlignment="left" onchange="${updateCountFunc}" onload="${updateCountFunc}"/>
        </div>
      </c:when>

      <%-- use a type ahead --%>
      <c:when test="${displayType eq 'typeAhead'}">
        <div class="param ${dependentClass}" data-type="type-ahead" dependson="${dependedParam}" name="${pNam}">
          <div id="${pNam}_display" data-multiple="true"></div>
          <html:hidden property="value(${pNam})" />
          <div class="type-ahead-help" style="margin:2px;">
            Begin typing to see suggestions to choose from (CTRL or CMD click to select multiple)<br/>
            Or paste a list of IDs separated by a comma, new-line, white-space, or semi-colon.<br/>
            <%-- wildcard support has been dropped due to SQL complications
            Or use * as a wildcard, like this: *your-term*
            --%>
          </div>
        </div>
      </c:when>
  
      <%-- use a multi-select box --%>
      <c:otherwise>
        <div class="param ${dependentClass}" data-type="multi-pick" dependson="${dependedParam}" name="${pNam}">
          <c:set var="initialCount" value="${fn:length(qP.currentValues)}"/>
          <imp:enumCountWarning enumParam="${qP}" initialCount="${initialCount}"/>
          <c:set var="changeCode" value="window.wdk.parameterHandlers.adjustEnumCountSelect('${qP.name}aaa')"/>
          <html:select property="array(${pNam})" multiple="1" styleId="${pNam}" onchange="${changeCode}">
            <html:options property="array(${pNam}-values)" labelProperty="array(${pNam}-labels)" />
          </html:select>
          <br/><imp:selectAllParamOpt enumParam="${qP}" onchange="${changeCode}"/>
        </div>
      </c:otherwise>
      
    </c:choose>
  </c:when> <%-- end of multipick --%>
  <c:otherwise> <%-- pick single item --%>
      <c:choose>
        <c:when test="${displayType eq 'radioBox'}">
          <div class="param ${dependentClass}" dependson="${dependedParam}" name="${pNam}">
            <ul>
              <c:forEach items="${qP.displayMap}" var="entity">
                <li ${v}>
                  <label>
                    <html:radio property="array(${pNam})" value="${entity.key}" /> <span>${entity.value}</span>
                  </label>
                </li>
              </c:forEach>
            </ul>
          </div>
        </c:when>
      
        <%-- use a type ahead --%>
        <c:when test="${displayType eq 'typeAhead'}">
          <div class="param ${dependentClass}" data-type="type-ahead" dependson="${dependedParam}" name="${pNam}">
            <div id="${pNam}_display" data-multiple="false"></div>
            <html:hidden property="value(${pNam})" />
            <div class="type-ahead-help" style="margin:2px;">
              Begin typing to see suggestions from which to choose<br/>
              <%-- Or use * as a wildcard, like this: *your-term* --%>
            </div>
          </div>
        </c:when>
  
        <c:otherwise>
          <%-- multiPick is false, use pull down menu --%>
          <div class="param ${dependentClass}" dependson="${dependedParam}" name="${pNam}">
            <html:select property="array(${pNam})" styleId="${pNam}">
              <c:set var="opt" value="${opt+1}"/>
              <c:set var="sel" value=""/>
              <c:if test="${opt == 1}"><c:set var="sel" value="selected"/></c:if>      
              <html:options property="array(${pNam}-values)" labelProperty="array(${pNam}-labels)"/>
            </html:select>
          </div>
        </c:otherwise>
      </c:choose>
  </c:otherwise> <%-- end of pick single item --%>
</c:choose>

<%-- display invalid terms, if any. --%>
<c:set var="invalidKey" value="${qP.name}_invalid" />
<c:set var="invalidTerms" value="${requestScope[invalidKey]}" />

<c:if test="${fn:length(invalidTerms) gt 0}">
  <div class="invalid-values">
    <p>Some of the option(s) you previously selected are no longer available.</p>
    <p>Here is a list of the values you selected (unavailable options are marked in red):</p>
    <ul>
      <c:forEach items="${invalidTerms}" var="invalidTerm">
        <li class="invalid">${invalidTerm}</li>
      </c:forEach>
    </ul>
  </div>
</c:if>
