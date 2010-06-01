<%@ taglib prefix="wdk" tagdir="/WEB-INF/tags/wdk" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="dType"><%= request.getParameter("dataType") %></c:set>
<c:set var="stepNum"><%= request.getParameter("prevStepNum") %></c:set>
<c:set var="add"><%= request.getParameter("isAdd") %></c:set>

<%--<wdk:addStepPopup_new model="${applicationScope.wdkModel}" rcName="${dType}" prevStepNum="${stepNum}" isAdd="${add}"/>--%>
<c:set var="displayType" value=""/>
<c:choose>
<c:when test="${dType == 'OrfRecordClasses.OrfRecordClass'}"><c:set var="displayType" value="ORF"/></c:when>
<c:when test="${dType == 'EstRecordClasses.EstRecordClass'}"><c:set var="displayType" value="EST"/></c:when>
<c:when test="${dType == 'SnpRecordClasses.SnpRecordClass'}"><c:set var="displayType" value="SNP"/></c:when>
<c:when test="${dType == 'AssemblyRecordClasses.AssemblyRecordClass'}"><c:set var="displayType" value="Assembly"/></c:when>
<c:when test="${dType == 'SequenceRecordClasses.SequenceRecordClass'}"><c:set var="displayType" value="Genomic Sequence"/></c:when>
<c:when test="${dType == 'dynSpanRecordClasses.DynSpanRecordClass'}"><c:set var="displayType" value="Dynamic Span"/></c:when>
<c:when test="${dType == 'IsolateRecordClasses.IsolateReocrdClass'}"><c:set var="displayType" value="Isolate"/></c:when>
<c:when test="${dType == 'SageTageRecordClasses.SageTagRecordClass'}"><c:set var="displayType" value="SAGE Tag"/></c:when>
<c:otherwise>
	<c:set var="displayType" value="Gene"/>
</c:otherwise>
</c:choose>
<table><tr><td style="vertical-align:top">
	<!--	<span class="heading">Instructions</span>-->
		<ul id="directions">
			<li><img src="<c:url value="wdk/images/step1.png"/>" width="20"/> Define region for current results of the strategy</li>
			<li><img src="<c:url value="wdk/images/step2.png"/>" width="20"/> Define region for results from the current step</li>
			<li><img src="<c:url value="wdk/images/step3.png"/>" width="20"/> Choose the operation to relate the two sets of regions</li>
			<li><img src="<c:url value="wdk/images/step4.png"/>" width="20"/> Choose the strands to which the operation should be applied</li>
			<li><img src="<c:url value="wdk/images/step5.png"/>" width="20"/> Decide which result set the results of this operation should come from</li>
		</ul>
	</td>
	<td style="vertical-align:top">
<div id="span_popup" width="100%" align="center">
	
	<input type="hidden" name="myProp(span_a)" value="${stepNum}" id="spanA"/>
	<input type="hidden" value="${dType}" id="typeA"/>
	<input type="hidden" value="" id="typeB"/>
	<input type="hidden" name="myProp(span_b)" value="" id="spanB"/>
	<table>
		<tr><th><span class="heading">${displayType}&nbsp;Results</span></th><th></th><th><span class="heading" id="fromAjax">&nbsp;Results</span></th></tr>
		<tr>
			<td id="left">
				<div class="controls">
					<img class="step_img" id="step_1" src="<c:url value="wdk/images/step1.png"/>"/>
					<select name="myProp(span_begin_a)"><option value="start">start</option><option value="stop">stop</option></select>
					<select name="myProp(span_begin_direction_a)"><option value="+">+</option><option value="-">-</option></select>
					<input type="text" value="0" name="myProp(span_begin_offset_a)"/><br><br>
					<select name="myProp(span_end_a)"><option value="start">start</option><option value="stop" SELECTED>stop</option></select>
					<select name="myProp(span_end_direction_a)"><option value="+">+</option><option value="-" SELECTED>-</option></select>
					<input type="text" value="0" name="myProp(span_end_offset_a)"/><br><br>
				</div>
			</td>
			<td id="middle" style="border-bottom:1px solid #BBBBBB">
					<img class="step_img" id="step_3" src="<c:url value="wdk/images/step3.png"/>"/>
					<input type="radio" value="overlap" name="myProp(span_operation)" id="span-operation-overlap">&nbsp;&nbsp;<label>Overlap</label><br/>
					<input type="radio" value="a_contain_b" name="myProp(span_operation)" id="span-operation-containing">&nbsp;&nbsp;<label>Containing</label><br/>
					<input type="radio" value="b_contain_a" name="myProp(span_operation)" id="span-operation-contained">&nbsp;&nbsp;<label>Contained In</label><br/>
				</div>
			</td>
			<td id="right">
				<div class="controls">
					<img class="step_img" id="step_2" src="<c:url value="wdk/images/step2.png"/>"/>
					<select name="myProp(span_begin_b)"><option value="start">start</option><option value="stop">stop</option></select>
					<select name="myProp(span_begin_direction_b)"><option value="+">+</option><option value="-">-</option></select>
					<input type="text" value="0" name="myProp(span_begin_offset_b)"/><br><br>
					<select name="myProp(span_end_b)"><option value="start">start</option><option value="stop" SELECTED>stop</option></select>
					<select name="myProp(span_end_direction_b)"><option value="+">+</option><option value="-" SELECTED>-</option></select>
					<input type="text" value="0" name="myProp(span_end_offset_b)"/><br><br>
					
				</div>
			</td>
		</tr>
		<tr>
			<td id="left"><div style="text-align:center;vertical-align:middle;">GRAPHICS</div></td>
			<td id="middle">
				<img class="step_img" id="step_4" src="<c:url value="wdk/images/step4.png"/>"/>
				<input type="radio" value="Both strands" name="myProp(span_strand)" id="strand-operation-both">&nbsp;&nbsp;<label>Both Strands</label><br/>
				<input type="radio" value="Same strand" name="myProp(span_strand)" id="strand-operation-same">&nbsp;&nbsp;<label>Same Strand</label><br/>
				<input type="radio" value="Opposite strand" name="myProp(span_strand)" id="strand-operation-opposite">&nbsp;&nbsp;<label>Opposite Strand</label><br/>
			</td>
			<td id="right"><div style="text-align:center;vertical-align:middle;">GRAPHICS</div></td>
		</tr>
		<tr>
			<td id="left" style="text-align:center">
				<img class="step_img" id="step_5" src="<c:url value="wdk/images/step5.png"/>"/>
				<input type="radio" name="myProp(span_output)" value="type of input A" id="left_output"/>&nbsp;<label>Select Set A as output</label></td>
				<td id="middle"></td>
				<td id="right" style="text-align:center"><input type="radio" name="myProp(span_output)" value="type of input B" id="left_output"/>&nbsp;<label>Select Set B as output</label></td>
		</tr>
	</table>
	<input type="submit" value="Get Result" style="margin-top:5px"/>

</div>
	</td></tr></table>
