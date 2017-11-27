<%@ taglib prefix="imp" tagdir="/WEB-INF/tags/imp" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="html" uri="http://struts.apache.org/tags-html" %>
<%@ taglib prefix="bean" uri="http://struts.apache.org/tags-bean" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%@ attribute name="paramGroup"
              required="true"
              description="Parameter group"
              type="java.util.Map"
%>
<%@ attribute name="groupDescription"
              required="false"
              description="Group description"
              type="java.lang.String"
%>

<div id="grp-descr">${groupDescription}</div>

<!-- get params from group -->

<c:forEach items="${paramGroup}" var="paramItem">
  <c:set var="pNam" value="${paramItem.key}" />
  <c:set var="qP" value="${paramItem.value}" />

  <c:set var="isHidden" value="${qP.isVisible == false}"/>
  <c:set var="isReadonly" value="${qP.isReadonly == true}"/>
  <c:set var="paramType" value="${qP.type}" />

  <%-- hide invisible params --%>
  <c:choose>
      <c:when test="${paramType eq 'TimestampParam'}">
          <imp:timestampParamInput qp="${qP}" />
      </c:when>
      <c:when test="${isHidden}">
        <c:choose>
          <c:when test="${pNam eq 'wdk_user_signature'}">
            <html:hidden property="value(${pNam})" value="${wdkUser.signature}"/>
          </c:when>
          <c:otherwise>
            <html:hidden property="value(${pNam})"/>
          </c:otherwise>
        </c:choose>
      </c:when>
      <c:otherwise> <%-- visible param --%>
          <%-- an individual param (can not use fullName, w/ '.', for mapped props) --%>

          <div class="param-item">
          <c:if test="${not empty qP.visibleHelp}">
            <p>${qP.visibleHelp}</p>
          </c:if>

            <label>
              <div class="param-item-label-wrapper">
                <span class="param-item-label-text">${qP.prompt}</span>
                <imp:image class="param-item-label-help help-link" style="cursor:pointer" title="${fn:escapeXml(qP.help)}" src="wdk/images/question.png" />
              </div>
            </label>
              <c:choose>
                  <c:when test="${paramType eq 'FilterParam'}">
                      <div class="param-control" id="${qP.name}aaa">
                        <imp:filterParamInput qp="${qP}" />
                      </div>
                  </c:when>
                  <c:when test="${paramType eq 'FilterParamNew'}">
                      <div class="param-control" id="${qP.name}aaa">
                        <imp:filterParamNewInput qp="${qP}"/>
                      </div>
                  </c:when>
                  <c:when test="${paramType eq 'EnumParam' || paramType eq 'FlatVocabParam'}">
                      <div class="param-control" id="${qP.name}aaa">
                          <imp:enumParamInput qp="${qP}" />
                      </div>
                  </c:when>
                  <c:when test="${paramType eq 'AnswerParam'}">
                      <div class="param-control" id="${qP.name}aaa">
                          <imp:answerParamInput qp="${qP}" />
                      </div>
                  </c:when>
                  <c:when test="${paramType eq 'DatasetParam'}">
                      <div class="param-control" id="${qP.name}aaa">
                          <imp:datasetParamInput qp="${qP}" />
                      </div>
                  </c:when>
                  <c:when test="${paramType eq 'StringParam'}">
                      <div class="param-control" id="${qP.name}aaa">
                          <imp:stringParamInput qp="${qP}" />
                      </div>
                  </c:when>
                  <c:otherwise>
                      <c:choose>
                          <c:when test="${isReadonly}">
                              <div class="param-control" id="${qP.name}aaa">
                                  <bean:write name="qForm" property="value(${pNam})"/>
                                  <html:hidden property="value(${pNam})"/>
                              </div>
                          </c:when>
                          <c:when test="${qP.class.name eq 'org.gusdb.wdk.model.jspwrap.StringParamBean' and qP.multiLine}">
                              <div class="param-control" id="${qP.name}aaa">
                                  <html:textarea styleId="${pNam}" property="value(${pNam})" rows="4" cols="50"/>
                              </div>
                          </c:when>
                          <c:otherwise>
                              <div class="param-control" id="${qP.name}aaa">
                                <div class="ui-state-error ui-corner-all">
                                  Unknown param type "${paramType}"
                                </div>
                              </div>
                          </c:otherwise>
                      </c:choose>
                  </c:otherwise>
              </c:choose>
          </div>
      </c:otherwise> <%-- end visible param --%>
  </c:choose>

</c:forEach> <%-- end of forEach params --%>

