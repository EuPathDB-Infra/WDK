<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="html" uri="http://jakarta.apache.org/struts/tags-html" %>
<%@ taglib prefix="bean" uri="http://jakarta.apache.org/struts/tags-bean" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="wdk" tagdir="/WEB-INF/tags/wdk" %>


<c:set var="strategy" value="${requestScope.wdkStrategy}"/>
<c:set var="importStrategy" value="${requestScope.importStrategy}" />
<c:set var="importStep" value="${requestScope.importStep}" />
<c:set var="allowBoolean" value="${requestScope.allowBoolean}"/>
<c:set var="wdkStep" value="${requestScope.wdkStep}"/>
<c:set var="action" value="${requestScope.action}"/>

<c:set var="spanOnly" value="false"/>
<c:set var="checked" value=""/>
<c:set var="buttonVal" value="Get Answer"/>


<c:if test="${importStep.dataType != wdkStep.dataType}">
	<c:set var="checked" value="checked=''"/>
	<c:set var="buttonVal" value="Continue"/>
	<c:set var="spanOnly" value="true"/>
</c:if>


<c:set var="wizard" value="${requestScope.wizard}"/>
<c:set var="stage" value="${requestScope.stage}"/>

<%-- determine current & next front step id --%>
<c:choose>
  <c:when test="${action eq 'add'}">
    <c:set var="rightFrontId" value="${wdkStep.frontId + 1}" />
  </c:when>
  <c:otherwise>
    <c:set var="rightFrontId" value="${wdkStep.frontId}" />
  </c:otherwise>
</c:choose>  
<c:set var="leftFrontId" value="${rightFrontId - 1}" />

<html:form styleId="form_question" method="post" enctype='multipart/form-data' action="/processFilter.do"  onsubmit="callWizard('wizard.do?action=${requestScope.action}&step=${wdkStep.stepId}&',this,null,null,'submit')">



<%-- display question param section --%>
<div class="filter params">
  <input type="hidden" name="importStrategy" value="${importStrategy.strategyId}"/>

  <div class="h2center">
    Add Step ${wdkStep.frontId + 1} from existing strategy: 
  </div>
  <div style="text-align:center;font-weight:bold;font-size:120%">${importStrategy.name}</div>
  <br>
</div>
  
  
<%-- display operators section --%>
<c:set var="type" value="${wdkStep.shortDisplayType}" />
<c:set var="allowSpan" value="${type eq 'Gene' || type eq 'Orf' || type eq 'SNP' || type eq 'Isolate'}" />

<div class="filter operators">
  <c:choose>
    <c:when test="${(wdkStep.isTransform || wdkStep.previousStep == null) && action == 'revise'}">
       <c:set var="nextStage" value="process_question" />
    </c:when>

    <c:otherwise>
      <span class="h2center">Combine ${wdkStep.displayType}s in Step ${leftFrontId} with ${importStrategy.displayType}s in Step ${rightFrontId}:</span>
      <div style="text-align:center" id="operations">
                <c:choose>
                    <c:when test="${allowBoolean == false}">
                        <c:set var="nextStage" value="span_from_strategy" />
                    </c:when>
                    <c:otherwise>
                        <c:set var="nextStage" value="process_boolean" />
                    </c:otherwise>
                </c:choose>

<%-- operators table --%>
<wdk:operators  allowSpan="${allowSpan}"
		operation="${param.operation}"
/>

      </div>
    </c:otherwise>
  </c:choose>
</div>

<html:hidden property="stage" styleId="stage" value="${nextStage}" />

<div id="boolean_button" class="filter-button"><html:submit property="questionSubmit" value="${buttonVal}"/></div>
</html:form>
