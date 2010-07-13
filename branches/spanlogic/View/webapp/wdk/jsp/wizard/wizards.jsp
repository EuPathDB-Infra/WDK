<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="wdk" tagdir="/WEB-INF/tags/wdk" %>

<c:set var="model" value="${applicationScope.wdkModel}" />
<c:set var="wizards" value="${model.wizardModel.wizards}" />

<wdk:addStepHeader />

<ul>
  <c:forEach items="${wizards}" var="wizard">
    <li class="wizard">
      <c:url var="wizardUrl" value="/wizard.do?wizard=${wizard.name}&step=" />
      <a href="javascript:callWizard('${wizardUrl}')">${wizard.display}</a>
      <div class="description">${wizard.description}</div>
    </li>
  </c:forEach>
</ul>
