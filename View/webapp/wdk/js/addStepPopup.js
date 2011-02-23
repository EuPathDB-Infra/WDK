var _action = "";
var original_Query_Form_Text;
var original_Query_Form_CSS = new Object();
var current_Front_Strategy_Id = null;

function showExportLink(stratId){
 	closeModal();
 	var exportLink = $("div#export_link_div_" + stratId);
 	exportLink.show();
}

function showPanel(panel) {
	if(panel == 'strategy_results'){
		if($("div#Strategies").attr("newstrategy") == 'true')
			initDYK(true);
		else
			initDYK(false);
	}else 
		initDYK(false);
	
	$("#strategy_tabs li").each(function(){
		if($("a", this).length > 0){
			var hidePanel = $("a", this).attr("id").substring(4);
			$("#tab_" + hidePanel).parent().removeAttr("id");
			$("#" + hidePanel).css({'position':'absolute','left':'-1000em','width':'100%','display':'none'});
		}
	});
	$("#tab_" + panel).parent().attr("id", "selected");
	$("#" + panel).css({'position':'relative','left':'auto','display':'block'});
	if (panel == 'strategy_results') {
		if($.cookie("refresh_results") == "true"){
			var currentStep = $("#Strategies div.selected");
			var active_link = $("a.results_link", currentStep);
			if(active_link.length == 0) active_link = $(".resultCount a.operation", currentStep);
			active_link.click();
			$.cookie("refresh_results", "false", { path : '/' });
		}
		$("body > #query_form").show();
		$("body > .crumb_details").show();
	}
	else {
		if (panel == 'search_history') updateHistory();
		if (panel == 'basket') showBasket();
		$("body > #query_form").hide();
		$("body > .crumb_details").hide();
	}
	setCurrentTabCookie('application', panel);
}

function showSaveForm(stratId, save, share){
	closeModal();
	$("div.save_strat_div").addClass("hidden");
	var saveForm = $("div#save_strat_div_" + stratId);
	var stratName = getStrategyOBJ(stratId).name;
	$("input[type=text]", saveForm).attr("value", stratName);
       if (save){
         $("form", saveForm).attr("action", "javascript:saveOrRenameStrategy(" + stratId + ", true, true, false)");
         $("span.h3left", saveForm).text("Save As");
         $("input[type=submit]", saveForm).attr("value", "Save");
         if (share) {
		  $("form", saveForm).append("<input type='hidden' name='action' value='share'/>");
                  $("form", saveForm).append("<input type='hidden' name='actionStrat' value='" + stratId + "'/>");
		  $("span.h3left", saveForm).text("First you need to Save it!");
         }
       }
       else{
         $("form", saveForm).attr("action", "javascript:saveOrRenameStrategy(" + stratId + ", true, false, false)");
         $("span.h3left", saveForm).text("Rename");
         $("input[type=submit]", saveForm).attr("value", "Rename");
       }
	saveForm.show();
         $("input[name='name']", saveForm).focus().select();
}

function closeModal(){
	$("div.modal_div").hide();
}

function validateSaveForm(form){
	var strat = getStrategyFromBackId(form.strategy.value);
        if (form.name.value == ""){
                var message = "<h1>You must specify a name for saving!</h1><input type='button' value='OK' onclick='$(\"div#diagram_" + strat.frontId + "\").unblock();$(\"div#search_history\").unblock();'/>";
                $("div#diagram_" + strat.frontId).block({message: message});
                $("div#search_history").block({message: message});
                return false;
        } else if (form.name.value.length > 200) {
                var message = "<h1>The name you have entered is too long.  Please enter a name that is at most 200 characters.</h1><input type='button' value='OK' onclick='$(\"div#diagram_" + strat.frontId + "\").unblock();$(\"div#search_history\").unblock();'/>";
                $("div#diagram_" + strat.frontId).block({message: message});
                $("div#search_history").block({message: message});
		return false;
	}
        return true;
}

function formatFilterForm(params, data, edit, reviseStep, hideQuery, hideOp, isOrtholog){
	//edit = 0 ::: adding a new step
	//edit = 1 ::: editing a current step
	$("div#query_tooltips").remove();
	var ps = document.createElement('div');
	var qf = document.createElement('div');
	var topMenu_script = null;
	qf.innerHTML = data;
	ps.innerHTML = params.substring(params.indexOf("<form"),params.indexOf("</form>") + 6);
	if($("script#initScript", ps).length > 0)
		topMenu_script = $("script#initScript", ps).text();
	var operation = "";
	var stepn = 0;
	var insert = "";
	var proto = "";
	var currStrategy = getStrategy(current_Front_Strategy_Id);
	var stratBackId = currStrategy.backId;
	var stp = null;
	var stepBackId = null;
	if(edit == 0){
		insert = reviseStep;
		if (insert == ""){
			stp = currStrategy.getLastStep();
			stepBackId = (stp.back_boolean_Id == "") ? stp.back_step_Id : stp.back_boolean_id;
		}else{
			stp = currStrategy.getStep(insert,false);
			stepBackId = insert;
		}
	}else{
		var parts = reviseStep.split(":");
		proto = parts[0];
		reviseStep = parseInt(parts[1]);
		stp = currStrategy.getStep(reviseStep,false);
		stepBackId = reviseStep;
		isSub = true;
		operation = parts[4];
	}
	var pro_url = "";
	if(edit == 0)
		pro_url = "processFilter.do?strategy=" + stratBackId + "&insert=" +insert + "&ortholog=" + isOrtholog;
	else{
		pro_url = "processFilter.do?strategy=" + stratBackId + "&revise=" + stepBackId;
	}
	var historyId = $("#history_id").val();
	
	if(edit == 0){
		var close_link = "<a class='close_window' href='javascript:closeAll(false)'><img src='wdk/images/Close-X-box.png'/></a>";
		var back_link = "<a id='back_to_selection' href='javascript:close()'><img src='wdk/images/backbox.png'/></a>";
	}else
		var close_link = "<a class='close_window' href='javascript:closeAll(false)'><img src='wdk/images/Close-X-box.png'/></a>";

	var quesTitle = data.substring(data.indexOf("<h1>") + 4,data.indexOf("</h1>")).replace(/Identify \w+( \w+)* based on/,"");
	
	var quesForm = $("#form_question",qf);
	if(quesForm[0].tagName != "FORM"){
		var f = document.createElement('form');
		$(f).attr("id",$(quesForm).attr("id"));
		$(f).html($(quesForm).html());
		quesForm = $(f);
	}
	var quesDescription = $("#query-description-section",qf);//data);
	var dataSources = $("#attributions-section",qf);
	$("input[value=Get Answer]",quesForm).val("Run Step");
	$("input[value=Run Step]",quesForm).attr("id","executeStepButton");
	$(".params", quesForm).wrap("<div class='filter params'></div>");
	$(".params", quesForm).attr("style", "margin-top:15px;");

        // hide the file upload box
        quesForm.find(".dataset-file").each(function() {
            $(this).css("display", "none");
        });
	
	// Bring in the advanced params, if exist, and remove styling
	var advanced = $("#advancedParams_link",quesForm);
	advanced = advanced.parent();
	advanced.remove();
	advanced.attr("style", "");
	$(".filter.params", quesForm).append(advanced);
	
	if(edit == 0){
		if(insert == "" || (stp.isLast && isOrtholog)){
			$(".filter.params", quesForm).prepend("<span class='form_subtitle'>Add&nbsp;Step&nbsp;" + (parseInt(stp.frontId)+1) + ": " + quesTitle + "</span></br>");		
		}else if (stp.frontId == 1 && !isOrtholog){
			$(".filter.params", quesForm).prepend("<span class='form_subtitle'>Insert&nbsp;Step&nbsp;Before&nbsp;" + (stp.frontId) + ": " + quesTitle + "</span></br>");
		}else if (isOrtholog){
			$(".filter.params", quesForm).prepend("<span class='form_subtitle'>Insert&nbsp;Step&nbsp;Between&nbsp;" + (stp.frontId) + "&nbsp;And&nbsp;" + (parseInt(stp.frontId)+1) + ": " + quesTitle + "</span></br>");		
		}else{
			$(".filter.params", quesForm).prepend("<span class='form_subtitle'>Insert&nbsp;Step&nbsp;Between&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;And&nbsp;" + (stp.frontId) + ": " + quesTitle + "</span></br>");		
		}
	}else{
		$(".filter.params", quesForm).prepend("<span class='form_subtitle'>Revise&nbsp;Step&nbsp;" + (stp.frontId) + ": " + quesTitle + "</span></br>");
	}
	if(edit == 0){
		if(insert == ""){
			$(".filter.params", quesForm).after("<div class='filter operators'><span class='form_subtitle'>Combine with Step " + (stp.frontId) + "</span><div id='operations'><table style='margin-left:auto; margin-right:auto;'><tr><td class='opcheck' valign='middle'><input type='radio' name='booleanExpression' value='INTERSECT' /></td><td class='operation INTERSECT'></td><td valign='middle'>&nbsp;" + (stp.frontId) + "&nbsp;<b>INTERSECT</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='UNION'></td><td class='operation UNION'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>UNION</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='NOT'></td><td class='operation MINUS'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>MINUS</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='RMINUS'></td><td class='operation RMINUS'></td><td>&nbsp;" + (parseInt(stp.frontId)+1) + "&nbsp;<b>MINUS</b>&nbsp;" + (stp.frontId) + "</td></tr></table></div></div>");
		}else{
			$(".filter.params", quesForm).after("<div class='filter operators'><span class='form_subtitle'>Combine with Step " + (parseInt(stp.frontId)-1) + "</span><div id='operations'><table style='margin-left:auto; margin-right:auto;'><tr><td class='opcheck' valign='middle'><input type='radio' name='booleanExpression' value='INTERSECT' /></td><td class='operation INTERSECT'></td><td valign='middle'>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>INTERSECT</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='UNION'></td><td class='operation UNION'></td><td>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>UNION</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='NOT'></td><td class='operation MINUS'></td><td>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>MINUS</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='RMINUS'></td><td class='operation RMINUS'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>MINUS</b>&nbsp;" + (parseInt(stp.frontId)-1) + "</td></tr></table></div></div>");
		}
	} else {
		if(stp.frontId != 1){
			$(".filter.params", quesForm).after("<div class='filter operators'><span class='form_subtitle'>Combine with Step " + (parseInt(stp.frontId)-1) + "</span><div id='operations'><table style='margin-left:auto; margin-right:auto;'><tr><td class='opcheck'><input id='INTERSECT' type='radio' name='booleanExpression' value='INTERSECT' /></td><td class='operation INTERSECT'></td><td>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>INTERSECT</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input id='UNION' type='radio' name='booleanExpression' value='UNION'></td><td class='operation UNION'></td><td>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>UNION</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input id='MINUS' type='radio' name='booleanExpression' value='NOT'></td><td class='operation MINUS'></td><td>&nbsp;" + (parseInt(stp.frontId)-1) + "&nbsp;<b>MINUS</b>&nbsp;" + (stp.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='RMINUS'></td><td class='operation RMINUS'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>MINUS</b>&nbsp;" + (parseInt(stp.frontId)-1) + "</td></tr></table></div></div>");
		}else{
			$(".filter.params", quesForm).after("<input type='hidden' name='booleanExpression' value='AND' />");
		}
	}
	if(edit == 0)	
		var action = "javascript:validateAndCall('add','" + pro_url + "', '" + stratBackId + "')";
	else
		var action = "javascript:validateAndCall('edit', '" + pro_url + "', '" + stratBackId + "', "+ parseInt(reviseStep) + ")";
	var formtitle = "";
	if(edit == 0){
		if(insert == "")
			formtitle = "<h1 style='font-size:130%;position:relative;margin-top: 4px;'>Add&nbsp;Step</h1>";
		else
			formtitle = "<h1  style='font-size:130%;position:relative;margin-top: 4px;'>Insert&nbsp;Step</h1>";
	}else{
		formtitle = "<h1  style='font-size:130%;position:relative;margin-top: 4px;'>Revise&nbsp;Step</h1>";
	}
	quesForm.attr("action",action);
	if(edit == 0)
		var header = "<span class='dragHandle'>" + back_link + " " + formtitle + " " + close_link + "</span>";
	else
		var header = "<span class='dragHandle'>" + formtitle + " " + close_link + "</span>";
		
	$("#query_form").html(header);
	if (hideQuery){
	        $(".filter.params", quesForm).remove();
	        $("input[name=questionFullName]", quesForm).remove();
	        $(".filter.operators", quesForm).width('auto');
	}else{
		$("div.filter div.params", quesForm).html(ps.getElementsByTagName('form')[0].innerHTML);
	}
	if (hideOp){
		$(".filter.operators", quesForm).remove();
		$(".filter.params", quesForm).after("<input type='hidden' name='booleanExpression' value='AND' />");
	}
	
	$("#query_form").append(quesForm);
	var tooltips = $("#query_form div.htmltooltip");
	if (tooltips.length > 0) {
		$('body').append("<div id='query_tooltips'></div>");
		tooltips.remove().appendTo("div#query_tooltips");
	}

	if(edit == 1)
		$("#query_form div#operations input#" + operation).attr('checked','checked'); 
	
	if(quesDescription.length > 0)
		$("#query_form").append("<div style='padding:5px;margin:5px 15px 5px 15px;border-top:1px solid grey;border-bottom:1px solid grey'>" + quesDescription.html() + "</div>");

	if(dataSources.length > 0)
		$("#query_form").append("<div style='padding:5px;margin:5px 15px 5px 15px;border-top:1px solid grey;border-bottom:1px solid grey'>" + dataSources.html() + "</div>");

	$("#query_form").append("<div class='bottom-close'><a href='javascript:closeAll(false)' class='close_window'>Close</a></div>");
	htmltooltip.render();
	setDraggable($("#query_form"), ".dragHandle");
	$("#query_form").fadeIn("normal");
	if(topMenu_script != null){
		var tms = topMenu_script.substring(topMenu_script.indexOf("{")+1,topMenu_script.indexOf("}"));
		eval(tms);
	}
	var root = $(".param-tree", $("#query_form")[0]);
	initTreeState(root);
	if(edit == 1)
		initParamHandlers(true, true);
	else
		initParamHandlers(true);
}

function validateAndCall(type, url, proto, rs){
	var valid = false;
	if($("div#query_form div.filter.operators").length == 0){
		valid = true;
	}else{
		if($(".filter.operators")){
			$(".filter.operators div#operations input[name='booleanExpression']").each(function(){
				if($(this)[0].checked) valid = true;
			});
		}
	}
	if(!valid){
		alert("Please select Intersect, Union or Minus operator.");
		return;
	}
	mapTypeAheads();
	window.scrollTo(0,0);
	if(type == 'add'){
		AddStepToStrategy(url, proto, rs);
	}else{
		EditStep(url, proto, rs);
	}
	return;
}

function getQueryForm(url,hideOp,isOrtholog, loadingParent){
    // retrieve the question form, but leave out all params
    	var questionName = parseUrlUtil("questionFullName", url)[0];
		var questionUrl = url + "&showParams=false&isInsert=" + isInsert;
		var paramsUrl = url + "&showParams=true&isInsert=" + isInsert;
	    original_Query_Form_Text = $("#query_form").html();
		if(loadingParent == undefined) loadingParent = "query_form";
		$.ajax({
			url: questionUrl,
			dataType:"html",
			beforeSend: function(){
				showLoading(loadingParent);
			},
			success: function(data){
				$.ajax({
					url:paramsUrl,
					dataType: "html",
					success: function(params){
						formatFilterForm(params,data,0,isInsert,false,hideOp,isOrtholog);
						try {
							customGetQueryForm();
						}
						catch(err) {
							// Do nothing?  If user hasn't defined 
							// customQueryForm, that's OK.
						}
						removeLoading(loadingParent);
					}
				});
			},
			error: function(data, msg, e){
				alert("ERROR \n "+ msg + "\n" + e + ". \nPlease double check your parameters, and try again." + 
                                      + "\nReloading this page might also solve the problem. \nOtherwise, please contact site support.");
			}
		});
}

function OpenOperationBox(stratId, insertId) {
	var selectedStrat = $("#query_form select#selected_strategy").val();
	var selectedName = null;//$("#query_form select#selected_strategy option[selected]").text();
	$("#query_form select#selected_strategy option").each(function(){
		if(this.selected) selectedName = $(this).text().replace(/^\s*/, ""); return;
	});
        if (insertId == undefined) insertId = "";
	var url = "processFilter.do?strategy=" + getStrategy(stratId).backId + "&insert=" + insertId + "&insertStrategy=" + selectedStrat +"&checksum=" + getStrategy(stratId).checksum;
	var oform = "<form id='form_question' enctype='multipart/form-data' action='javascript:validateAndCall(\"add\",\""+ url + "\", \"" + getStrategy(stratId).backId + "\")' method='post' name='questionForm'>";
	var cform = "</form>";
	var ops = "<div class='filter operators'><span class='form_subtitle' style='padding:0 20px'>Combine <b><i>" + getStrategy(stratId).name + "</i></b> with <b><i>" + selectedName + "</i></b></span><div id='operations'><table style='margin-left:auto; margin-right:auto;'><tr><td class='opcheck' valign='middle'><input type='radio' name='booleanExpression' value='INTERSECT' /></td><td class='operation INTERSECT'></td><td valign='middle'>&nbsp;" + (stp.frontId) + "&nbsp;<b>INTERSECT</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='UNION'></td><td class='operation UNION'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>UNION</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='NOT'></td><td class='operation MINUS'></td><td>&nbsp;" + (stp.frontId) + "&nbsp;<b>MINUS</b>&nbsp;" + (parseInt(stp.frontId)+1) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' name='booleanExpression' value='RMINUS'></td><td class='operation RMINUS'></td><td>&nbsp;" + (parseInt(stp.frontId)+1) + "&nbsp;<b>MINUS</b>&nbsp;" + (stp.frontId) + "</td></tr></table></div></div>"
	var button = "<div style='text-align:center'><input type='submit' value='Add Strategy' /></div>";
	ops = oform + ops + button + cform;
	$("#query_form div#query_selection").replaceWith(ops);
}

function openFilter(dtype,strat_id,step_id,isAdd){
	if(openDetail != null) hideDetails();
	$("#strategy_results div.attributesList").hide();
	var isFirst = false;
	steps = getStrategy(strat_id).Steps;
	if(step_id == undefined){
		isFirst = true;
	}else{
		stp = getStrategy(strat_id).getStep(step_id,false)
		if(stp != null && stp.frontId == 1 && !isAdd) isFirst = true;
	}
	current_Front_Strategy_Id = strat_id;
	var url = "wdk/jsp/addStepPopup.jsp?dataType=" + dtype + "&prevStepNum=" + step_id + "&isAdd=" + isAdd;
	$.ajax({
		url: url,
		dataType: "html",
		beforeSend: function(){
			$("#query_form").remove();
			$("#Strategies div a#filter_link span").css({opacity: 1.0});
			$("#Strategies div#diagram_" + current_Front_Strategy_Id + " a#filter_link span").css({opacity: 0.4});
		},
		success: function(data){
			dykClose();
			$("body").append(data);
			original_Query_Form_CSS.maxW = $("#query_form").css("max-width");
			original_Query_Form_CSS.minW = $("#query_form").css("min-width");
			$("#query_form select#selected_strategy option[value='" + getStrategy(strat_id).backId + "']").remove();
			if(isAdd)
				$("#query_form h1#query_form_title").html("Add&nbsp;Step");
			else
				$("#query_form h1#query_form_title").html("Insert&nbsp;Step");
			if(isFirst){
				$("#query_form #selected_strategy,#continue_button").attr("disabled","disabled");
				$("#query_form #transforms a").attr('href',"javascript:void(0);").addClass("disabled");
			}else{
				$("#query_form #continue_button").click(function(){
				original_Query_Form_Text = $("#query_form").html();
				if($("#query_form select#selected_strategy").val() == "--")
						alert("Please select a strategy from the list.");
					else
						OpenOperationBox(strat_id, (isAdd ? undefined : step_id));
					return false;
				});
		
				$("#query_form #continue_button_transforms").click(function(){
					original_Query_Form_Text = $("#query_form").html();
					getQueryForm($("#query_form select#transforms").val(),true);
				});
			}
			if(!isAdd){
			$("#query_form ul#transforms a").each(function(){
				stp = getStrategy(strat_id).getStep(step_id,false);
				fid = parseInt(stp.frontId);
				if(fid > 1){
					var value = $(this).attr('href');
					var transformParams = value.match(/\w+_result=/gi);
					for (var i in transformParams) {
						value = value.split(transformParams[i]);
						var stpId = value[1].split("&");
						prevStp = getStrategy(strat_id).getStep(fid-1,true);
						if(prevStp.back_boolean_Id != null && prevStp.back_boolean_Id != "")
							stpId[0] = prevStp.back_boolean_Id;
						else
							stpId[0] = prevStp.back_step_Id;
						value[1] = stpId.join("&");
						value = value.join(transformParams[i]);
					}
					$(this).attr('href',value);
				}
			});
			}
			setDraggable($("#query_form"), ".handle");
		},
		error: function(){
			alert("Error getting the needed information from the server \n Please contact the system administrator");
		}
	});
}

function close(ele){
	cd = $("#query_form");
	$(cd).html(original_Query_Form_Text);
	$("#query_form").css("max-width",original_Query_Form_CSS.maxW);
	$("#query_form").css("min-width",original_Query_Form_CSS.minW);
	setDraggable($("#query_form"), ".dragHandle");
	
	$("#query_form #continue_button").click(function(){
		original_Query_Form_Text = $("#query_form").html();
		OpenOperationBox(strat_id, undefined);
		return false;
	});

	$("#query_form #continue_button_transforms").click(function(){
		original_Query_Form_Text = $("#query_form").html();
		getQueryForm($("#query_form select#transforms").val(),true);
	});
}

function closeAll(hide,as){
	if(hide)
		$("#query_form").hide();
	else
		$("#query_form").remove();
	isInsert = "";
	$("#Strategies div a#filter_link span").css({opacity: 1.0});
}

