/* jshint ignore:start */

// FIXME jshint errors

window.wdk.util.namespace("window.wdk.strategy.view", function(ns, $) {
  "use strict";

  var controller = wdk.strategy.controller,
      StrategyView = wdk.views.strategy.StrategyView,
      StepBoxView = wdk.views.strategy.StepBoxView,
      StepDetailView = wdk.views.strategy.StepDetailView,
      preventEvent = wdk.fn.preventEvent,
      killEvent = wdk.fn.killEvent;

  // temp reference to wdk.strategy.model
  var modelNS = wdk.strategy.model;

  //CONSTANTS
  var booleanClasses = "box row2 arrowgrey operation ";
  var firstClasses = "box row2 arrowgrey simple";
  var transformClasses = "row2 transform"; 
  var operandClasses = "box row1 arrowgrey simple";

  //Tooltips for step boxes
  var addStepTooltip = "Add a step to your search strategy. The results "+
      "of the new step will be combined with your current result. The step can be "+
      "a search or a transform (e.g. to find orthologs).";
  var stepBoxTooltip = function(filterName) {
      var filterText = (!filterName || filterName == "" ||
          filterName == 'All Results') ? "" : " (filtered by " + filterName + ")";
      return "Show Results" + filterText + ".  Click Edit to make changes."; };

  //Popup messages
  var insert_popup = "Insert a new step to the left of this one, by either " +
      "running a new query or choosing an existing strategy";
  var delete_popup = "Delete this step from the strategy; if this step is " +
      "the only step in this strategy, this will delete the strategy also";
  var x_popup = "Close this popup";

  var save_warning = "<i><b>Important!</b> <ul class='cirbulletlist'>" +
      "<li>You are saving/sharing the search template, not the IDs in your " +
      "result.</li><li>The IDs in <b>any step result might change</b> upon a" +
      "new release (every other month), and we cannot recover your old " +
      "result.</li><li>To keep a copy of your current result please " +
      "<a href='javascript:void(0)'>download your IDs</a>.</li></ul></i><br>";

  //Simple Steps
  var ss_rename_popup = "Rename this search";
  var ss_view_popup = "View the results of this search in the Results area below";
  var ss_analyze_popup = "Analyze the results of this search in the Results area below";
  var ss_edit_popup = "Revise the parameters of this search and/or its " +
      "combine operation";
  var ss_expand_popup = "Expand this step in a new panel to add nested steps." +
      " (Use this to build a non-linear strategy)";

  //Substrategies
  var sub_edit_expand_openned = "Revise the nested strategy in the open " +
      "panel below";  
  var sub_rename_popup = "Rename this nested strategy";             
  var sub_view_popup = "View the results of this nested strategy in the " +
      "Results area below";
  var sub_analyze_popup = "Analyze the results of this nested strategy in " +
      "the Results area below";
  var sub_edit_popup = "Open this nested step to revise";
  var sub_expand_popup = "Open into a new panel to add or edit nested steps";
  var sub_collapse_popup = "Convert a single-step nested strategy back to a " +
      "normal step";

  //Variables
  var has_invalid = false;

  // MANAGE THE DISPLAY OF THE STRATEGY BASED ON THE ID PASSED IN
  function displayModel(strat) {
    if (wdk.strategy.controller.strats) {
      $("#strat-instructions").remove();
      $("#strat-instructions-2").remove();
      // For IE : when instructions are shown, need to specify 'overflow : visible'
      // Need to remove this inline style when instructions are removed
      $("#Strategies").removeAttr("style");

      if (strat.isDisplay) {
        var div_strat = document.createElement("div");

        div_strat.setAttribute("data-step-id", strat.JSON.steps[strat.JSON.steps.length].id);
        div_strat.setAttribute("data-saved", strat.JSON.saved);
        div_strat.setAttribute("data-name", strat.name||"");
        div_strat.setAttribute("data-description", strat.description||"");
        div_strat.setAttribute("data-back-id", strat.backId);
        div_strat.setAttribute("data-is-public", strat.isPublic);
        div_strat.setAttribute("data-valid", strat.isValid);
        $(div_strat).addClass("strategy-data");
        $(div_strat).attr("id","diagram_" + strat.frontId).addClass("diagram");

        var div_steps_section = document.createElement("div");
        div_steps_section.setAttribute('class','diagramSection scrollableSteps');

        var div_steps = document.createElement("div");
        div_steps.setAttribute('class','stepWrapper');
        //$(div_steps).css({"width":(118 * strat.Steps.length) + "px"});
        $(div_steps).css({"width":(9.8 * strat.Steps.length) + "em"});

        var close_span = document.createElement('span');
        $(close_span)
            .addClass("closeStrategy")
            .html("<a href='#'>"+
                "<img alt='Click here to close the strategy (it will only be " +
                "removed from the display)' src='" + wdk.assetsUrl('wdk/images/close.gif') + "'" +
                "title='Click here to close the strategy (you can open it from the All tab)'/></a>");
        $(div_strat).append(close_span);

        var stratNameMenu = createStrategyName(strat);
        $(div_strat).append(stratNameMenu[0]);
        $(div_strat).append(stratNameMenu[1]);
        $(div_strat).append(createParentStep(strat));
        var displaySteps = createSteps(strat,div_steps);
        $(div_strat).append(createRecordTypeName(strat));
        var button = document.createElement('a');
        var lsn = strat.getStep(strat.Steps.length,true).back_boolean_Id;

        if (lsn == "" || lsn == null) {
          lsn = strat.getStep(strat.Steps.length, true).back_step_Id;  
        }
        var dType = strat.dataType;
        $(button)
            .attr("id","filter_link")
            .attr("href","javascript:wdk.addStepPopup.openFilter('" + dType + "'," +
                strat.frontId + "," + lsn + ",true)")
            .attr("onclick","this.blur()")
            // FIXME CSS
            .attr("onmouseover","jQuery(this).find('span').css('color','pink')")
            .attr("onmouseout","jQuery(this).find('span').css('color','white')")
            .addClass("filter_link redbutton step-elem")
            .attr("title",addStepTooltip)
            .html("<span>Add Step</span>");
        var buttonDiv = document.createElement('div');
        var diagramWrapper = document.createElement('div');
        buttonDiv.setAttribute('class','addStepButton');
        $(buttonDiv).append(button);
        $(div_steps_section).append(div_steps);
        $(diagramWrapper)
          .append(div_steps_section)
          .append(buttonDiv)
          .appendTo(div_strat)
          .addClass("diagram-wrapper");

        if (has_invalid) {
          $(div_strat).append(createInvalidText());
        }

        has_invalid = false;

        var strategyView = new StrategyView({
          el: div_strat,
          model: strat,
          controller: controller
        });

        return div_strat;
      }
    }
    return null;
  }

  // controls visibility of step box edit icons
  /* using css now - see Strategy.css
  function setEditIconToggling() {
    $('.edit-step-pane')
      .mouseenter(function(){
        $('.edit-step').css('display','inline-block'); })
      .mouseleave(function(){
        $('.edit-step').css('display','none'); });
  }
  */

  // HANDLES THE CREATION OF THE STEP BOX --
  // This function could be broken down to smaller bites
  // based on the type of step -- future work
  function createSteps(strat,div_strat){
    var zIndex = 80;
    var stepBoxViews = [];
    var stepDetailView;
    var stepdiv;
    var cStp;
    var jsonStep;
    var prevJsonStep;

    for (var ind=0; ind < strat.Steps.length; ind++) {  //cStp in strat.Steps
      cStp = strat.getStep(ind+1,true);
      jsonStep = strat.JSON.steps[cStp.frontId];
      prevJsonStep = (ind == 0) ? null : strat.JSON.steps[strat.getStep(ind,true).frontId];

      if (cStp.isboolean || cStp.isSpan) {
        //Create the two layered Boolean Steps
        stepdiv = multiStep(cStp, prevJsonStep, jsonStep, strat.frontId);
      } else {
        //Create Single Layered Steps like First Step or Transforms
        stepdiv = singleStep(cStp, prevJsonStep, jsonStep,strat.frontId);
      }

      $(stepdiv).find('[id^=step_]').each(function(index, el) {
        stepDetailView = new StepDetailView({
          el: $(el).find('.crumb_details'),
          model: jsonStep,
          controller: controller,
          strategy: strat,
          isBoolean: index === 1,
          previousStep: prevJsonStep
        });

        stepBoxViews.push(new StepBoxView({
          el: el,
          model: jsonStep,
          controller: controller,
          strategy: strat,
          isBoolean: index === 1,
          previousStep: prevJsonStep,
          stepDetailView: stepDetailView
        }));
      });

      $(stepdiv).css({'z-index' : zIndex});
      $(div_strat).append(stepdiv);
      zIndex--; // DO NOT DELETE, needed for correct display in IE7.
    }
    //setEditIconToggling();
  }

  //Creates the boolean Step and the operand step displayed above it
  function multiStep(modelstep, prevjsonstep, jsonStep, sid) {
    // Create the boolean venn diagram box
    var filterImg = "";
    var bool_link = "";
    var details_link = "wdk.step.showDetails(this)";

    if (modelstep.isSpan) {
      details_link = "void(0)";
      jsonStep.operation = "SPAN " + getSpanOperation(jsonStep.params);
    }

    if (jsonStep.isValid) {
      bool_link = "wdk.strategy.controller.newResults(" + sid + "," +
          modelstep.frontId + ", true)";
    }

    if (jsonStep.filtered) {
      filterImg = "<span class='filterImg'><img src='" + wdk.assetsUrl('wdk/images/filter-short.png') + "'" +
          "style='height:10px;position:relative;top:0px'/></span>";
    }

    var displayType = (jsonStep.results > 1) ? jsonStep.shortDisplayTypePlural : jsonStep.shortDisplayType;
    
    var boolinner = ""+
        "<div id='" + sid + "|" + modelstep.back_boolean_Id + "|" + jsonStep.operation + "' class='divlink results_link step-elem' "+
        "     title=\""+stepBoxTooltip(jsonStep.filterName)+"\" href='javascript:void(0)' style='cursor:pointer'> " +
        //"     onmouseover=\"jQuery(this).find('.edit-icon').css('display','inline')\" onmouseout=\"jQuery(this).find('.edit-icon').css('display','inline')\">" +
        "  <img style='width:50px;height:26px' src='" + wdk.assetsUrl('wdk/images/transparent1.gif') + "'>" +
        "  <div style='position:absolute;top:-9px;right:10px;width:19px;height:19px;'></div>"+
        "  <a href='#' class='edit-icon step-elem' style='display:inline;position:absolute;top:-8px;right:2px' " +
        "     >" + getEditImage('boolean')+"</a><br/>"+
        "  <div class='crumb_details'></div>" +
        "  <h6 class='resultCount'>" +
        "    <span class='operation'>" + jsonStep.results + "&nbsp;" + displayType + "</span>" +
        "  </h6>" +
           filterImg +
        "</div>";
    
    if (!modelstep.isLast) {
      if (modelstep.nextStepType == "transform") {
        boolinner = boolinner + "<div class='arrow right size3'></div>";
      } else {
        boolinner = boolinner + "<div class='arrow right size2'></div>";
      }
    }

    var boolDiv = document.createElement('div');
    $(boolDiv).attr("id","step_" + modelstep.frontId).attr("style","border-radius:8px")
        .addClass(booleanClasses + jsonStep.operation).html(boolinner);

    $(".crumb_details", boolDiv)
        .replaceWith(createDetails(modelstep, prevjsonstep, jsonStep, sid));
    var stepNumber = document.createElement('span');
    $(stepNumber).addClass('stepNumber').text("Step " + modelstep.frontId);

    //Create the operand Step Box
    var childStp = jsonStep.step;  
    var uname = "";
    var fullName = "";

    if (childStp.name == childStp.customName) {
      uname = childStp.shortName;
      fullName = childStp.name;
    } else {
      uname = (childStp.customName.length > 15) ?
          childStp.customName.substring(0,12) + "..." :
          childStp.customName; 
      fullName = childStp.customName;
    }

    var childfilterImg = "";

    if (childStp.filtered) {
      childfilterImg = "<span class='filterImg'><img src='" + wdk.assetsUrl('wdk/images/filter-short.png') + "' " +
          "style='height:10px;position:relative;top:1px'/></span>";
    }

    var childinner = ""+
    "    <div style='cursor:pointer' title=\""+stepBoxTooltip(childStp.filterName)+"\" class='results_link crumb_name divlink step-elem' "+
      "         href='#'>"+
      "        <a href='#'"+
      "           class='edit-icon step-elem' id='stepId_" + modelstep.frontId + "' style='display:inline;position:absolute;right:-6px;top:-7px'>"+
      getEditImage('top')+"</a>"+
      "      <h4>"+
      "        <span id='fullStepName' style='font-weight:bold;position:relative;top:2px'>" + fullName + "</span>"+
	//"        <div style='position:absolute;top:-6px;right:-8px;width:19px;height:19px;'></div>"+
      "        <div class='crumb_details'></div>"+
      "      </h4>"+
      "      <h6 class='resultCount'>" + childStp.results + "&nbsp;" + wdk.util.getDisplayType(childStp) + "</h6>"+
      childfilterImg +
      "    </div>"+
      "<img class='arrow down' src='" + wdk.assetsUrl('wdk/images/arrow_chain_down2.png') + "' alt='equals'>";

    var child_invalid = null;

    if (!childStp.isValid) {
      child_invalid = createInvalidDiv();
      $(child_invalid).attr("id", sid + "_" + modelstep.frontId);
      $("img", child_invalid).click(function() {
        var iv_id = $(this).parent().attr("id").split("_");
        $("div#diagram_" + iv_id[0] + " div#step_" + iv_id[1] +
            "_sub div.crumb_menu a.edit_step_link").click();
      });
    }
    
    var childDiv = document.createElement('div');

    if (child_invalid != null) {
      childDiv.appendChild(child_invalid);
    }

    $(childDiv).attr("id","step_" + modelstep.frontId + "_sub")
        .addClass(operandClasses).append(childinner);
    $(".crumb_details", childDiv)
        .replaceWith(createDetails(modelstep, prevjsonstep, childStp, sid));
    
    // Create the background div for a collapsed step if step is expanded
    var bkgdDiv = null;

    if (childStp.isCollapsed) {
      var ss_name = childStp.strategy.name.length > 15 ?
          childStp.strategy.name.substring(0,12) + "..." :
          childStp.strategy.name; 
      $(".crumb_name span#name", childDiv).text(ss_name);
      $("span#fullStepName", childDiv).text(childStp.strategy.name);
      bkgdDiv = document.createElement("div");
      $(bkgdDiv).addClass("expandedStep");
    }

    var stepbox = document.createElement('div');
    stepbox.setAttribute('class', 'stepBox');

    if (bkgdDiv != null) {
      stepbox.appendChild(bkgdDiv);
    }

    stepbox.appendChild(childDiv);
    stepbox.appendChild(boolDiv);
    stepbox.appendChild(stepNumber);
    return stepbox;
  }

  // type can be: 'top', 'bottom', 'transform', or 'boolean'
  function getEditImage(type) {
    return '<div class="edit-step"><span>Edit</span></div>';

    /* replaced styles with css (see Strategy.css)
    var boxStyle = 'display:none; position:relative; width:24px; height:10px; background-color:white;' +
        'text-align:center;color:black;border:1px solid black;border-radius:4px;';
    var spanStyle = 'font-size:10px;';

    if (type == 'boolean') { boxStyle += "top:3px"; }
    if (type == 'top') { spanStyle += "position:relative; top:-5px"; }

    return "<div class='edit-step' style='" + boxStyle + "'" +
      "onmouseover=\"jQuery(this).css('background-color','#FFFFA0')\" " +
      "onmouseout=\"jQuery(this).css('background-color','white')\" " +
      "><span style='"+spanStyle+"'>Edit</span></div>";
    */
    
    /* Old style with Edit icon being swapped images (left in for easy revert) */
    //var style = (type == 'boolean') ? "display:none;position:relative;top:3px" : "display:none";
    //return "<img class='edit-step' style='width:24px;"+style+"' src='wdk/images/edit-step-word-large.png' "+
    //  "onmouseover=\"jQuery(this).attr('src','wdk/images/edit-step-word-large-yel.png')\" " +
    //  "onmouseout=\"jQuery(this).attr('src','wdk/images/edit-step-word-large.png')\"/>";
  }

  //Creates all steps that are on the bottom line only ie. this first step and transform steps
  function singleStep(modelstep, prevjsonstep, jsonStep, sid) {
    var uname = "";
    var fullName = "";

    if (jsonStep.name == jsonStep.customName) {
      uname = jsonStep.shortName;
      fullName = jsonStep.name;
    } else {
      uname = (jsonStep.customName.length > 15)?jsonStep.customName.substring(0,12) + "...":jsonStep.customName; 
      fullName = jsonStep.customName;
    }

    var filterImg = "";
    if (jsonStep.filtered) {
      var filterImgOffset = modelstep.isTransform ? ";right:5px" : "";
      filterImg = "<span class='filterImg'><img src='" + wdk.assetsUrl('wdk/images/filter-short.png') + "' " +
          "style='height:10px;position:relative;top:1px"+filterImgOffset+"'/></span>";
    }

    var editIconOffset = modelstep.isTransform ? "right:6px;top:-5px" : "right:-6px;top:-7px";
    var boxType = modelstep.isTransform ? 'transform' : 'bottom';
    //var editIconWinOffset = modelstep.isTransform ? "right:0px;top:-4px" : "right:-8px;top:-8px";
    var inner = ""+
      "    <div style='cursor:pointer' title=\""+stepBoxTooltip(jsonStep.filterName)+"\" class='results_link crumb_name divlink step-elem' "+
      "         href='#'> "+
      "      <a href='#' class='edit-icon step-elem'"+
      "           id='stepId_" + modelstep.frontId + "' " +
      "           style='display:inline;position:absolute;"+editIconOffset+"'>"+getEditImage(boxType)+"</a>"+
      "      <h4>"+
      "        <span id='fullStepName' style='font-weight:bold;position:relative;top:2px'>" + fullName + "</span>"+
      "        <div class='crumb_details'></div>"+
      "      </h4>"+
      "      <h6 class='resultCount'>" + jsonStep.results + "&nbsp;" + wdk.util.getDisplayType(jsonStep) + "</h6>"+
      filterImg +
      "    </div>";

    if (!modelstep.isLast) {
      if (modelstep.isTransform) {
        if (modelstep.nextStepType == "transform") {
          inner = inner + "<div class='arrow right size5'></div>";
        } else {
          inner = inner + "<div class='arrow right size4'></div>";
        }
      } else {
        if (modelstep.nextStepType == "transform") {
          inner = inner + "<div class='arrow right size5'></div>";
        } else {
          inner = inner + "<div class='arrow right size1'></div>";
        }
      }
    }

    var step_invalid = null;

    if (!modelstep.isTransform && !jsonStep.isValid) {
      step_invalid = createInvalidDiv();
      $(step_invalid).attr("id",sid+"_"+modelstep.frontId);
    }

    var singleDiv = document.createElement('div');

    if (step_invalid != null) {
      singleDiv.appendChild(step_invalid);
    }

    $(singleDiv).attr("id","step_" + modelstep.frontId + "_sub").append(inner);
    var stepNumber = document.createElement('span');
    $(stepNumber).addClass('stepNumber').text("Step " + modelstep.frontId);

    if (modelstep.isTransform) {
      $(singleDiv).addClass(transformClasses);
    } else {
      $(singleDiv).addClass(firstClasses);
    }

    $(".crumb_details", singleDiv)
        .replaceWith(createDetails(modelstep, prevjsonstep, jsonStep, sid));
    var stepbox = document.createElement('div');
    stepbox.setAttribute('class', 'stepBox');
    stepbox.appendChild(singleDiv);
    stepbox.appendChild(stepNumber);
    return stepbox;  
  }

  //HANDLE THE CREATION OF THE STEP DETAILS BOX
  function createDetails(modelstep, prevjsonstep, jsonStep, sid) {
    var strat = modelNS.getStrategy(sid);
    var detail_div = document.createElement('div');
    detail_div.id = 'crumb_details_' + jsonStep.id;
    $(detail_div).addClass("crumb_details").attr("disp","0");
    $(detail_div).attr("style","text-align:center");

    if (jsonStep.isboolean && !jsonStep.isCollapsed) {
      $(detail_div).addClass("operation_details");
    }

    var name = jsonStep.customName;
    var questionName = jsonStep.questionName;
    var filteredName = "";

    if (jsonStep.filtered) {
      filteredName = "<span class='medium'><b>Applied Filter:&nbsp;</b>" +
          jsonStep.filterName + "</span><hr>";
    }

    if (jsonStep.isCollapsed) {
      name = jsonStep.strategy.name;
    } else if (jsonStep.isboolean) {
      if (jsonStep.step.isCollapsed) {
        name = jsonStep.step.strategy.name;
      } else {
        name = jsonStep.step.customName;
      }
    }

    var collapsedName = escape(name);//"Nested " + name;

    if (jsonStep.isboolean && !jsonStep.isCollapsed) {
      name = "<ul class='question_name'><li>STEP " + modelstep.frontId +
          " : Step " + (modelstep.frontId - 1) + "</li><li class='operation " +
          jsonStep.operation + "'></li><li>" + name + "</li></ul>";
    } else {
      name = "<p class='question_name'><span>STEP " + modelstep.frontId +
          " : " + name + "</span></p>";
    }

    var parentid = modelstep.back_step_Id;

    if (modelstep.back_boolean_Id != null &&
        modelstep.back_boolean_Id.length != 0) {
      parentid = modelstep.back_boolean_Id;
    }
    
    var params = jsonStep.params;
    var params_table = "";

    if (jsonStep.isboolean && !jsonStep.isCollapsed) {
      var url = "wizard.do?action=revise&step=" + modelstep.back_boolean_Id + "&";
      var oform = "<form id='form_question' class='clear' " +
          "enctype='multipart/form-data' name='questionForm'>";
      var cform = "</form>";
      var stage_input = "<input type='hidden' id='stage' value='process_boolean'/>";
      params_table = "<div class='filter operators'>" +
          "<span class='form_subtitle' style='padding-right:20px'>" +
          "<b>Revise Operation</b></span><div id='operations'>" +
          "<table style='margin-left:auto; margin-right:auto;'>" +
          "<tr><td class='opcheck' valign='middle'><input type='radio' " +
          "name='boolean' value='INTERSECT' /></td><td class='operation " +
          "INTERSECT'></td><td valign='middle'>&nbsp;" +
          (parseInt(modelstep.frontId, 10)-1) + "&nbsp;<b>INTERSECT</b>&nbsp;" +
          (modelstep.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" +
          "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'>" +
          "<input type='radio' name='boolean' value='UNION'></td>" +
          "<td class='operation UNION'></td><td>&nbsp;" +
          (parseInt(modelstep.frontId, 10)-1) + "&nbsp;<b>UNION</b>&nbsp;" +
          (modelstep.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" +
          "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td class='opcheck'>" +
          "<input type='radio' name='boolean' value='MINUS'></td>" +
          "<td class='operation MINUS'></td><td>&nbsp;" +
          (parseInt(modelstep.frontId, 10)-1) + "&nbsp;<b>MINUS</b>&nbsp;" +
          (modelstep.frontId) + "</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" +
          "&nbsp;&nbsp;&nbsp;</td><td class='opcheck'><input type='radio' " +
          "name='boolean' value='RMINUS'></td><td class='operation RMINUS'>" +
          "</td><td>&nbsp;" + (modelstep.frontId) + "&nbsp;<b>MINUS</b>&nbsp;" +
          (parseInt(modelstep.frontId, 10)-1) + "</td></tr></table></div></div>";
      var button = "<div style='text-align:center'>" +
          "<input type='submit' value='Revise' /></div>";
      params_table = oform + stage_input + params_table + button + cform;
    } else if (jsonStep.isCollapsed) {
      params_table = "<div>The nested strategy gets opened below.</div>";
    } else if (params != undefined && params.length != 0) {
      params_table = createParameters(params, modelstep.isSpan && jsonStep.id ==
          modelstep.back_boolean_Id);
    }
    var hideOp = false;
    var hideQu = false;
    if (jsonStep.isCollapsed) {
      // substrategy
      var rename_step = "<a title='" + sub_rename_popup +
          "' class='rename_step_link' href='#'>Rename</a>&nbsp;|&nbsp;";

      var view_step = "<a title='" + sub_view_popup + "' " +
          "class='view_step_link' href='#'>View</a>&nbsp;|&nbsp;";

      var analyze_step = "<a title='" + sub_analyze_popup + "' " +
          "class='analyze_step_link' href='#'>Analyze</a>&nbsp;|&nbsp;";

      var disab = "";
      var oM = "Open Nested Strategy";
      var moExp = sub_expand_popup;
      var moEdit = sub_edit_popup;

      if (jsonStep.strategy.order > 0) {
        disab = "disabled";
        oM = "Already Open Below...";
        moExp = sub_edit_expand_openned;
        moEdit = sub_edit_expand_openned;
      }

      var collapseDisabled = "";

      if (!jsonStep.isUncollapsible) {
        collapseDisabled = "disabled";
      }
      
      var edit_step = "<a title='" + moEdit + "' class='expand_step_link " +
          disab + "' href='#'>Revise</a>&nbsp;|&nbsp;";
      
      var collapse_step = "<a title='" + sub_collapse_popup +
          "' class='collapse_step_link " + collapseDisabled +
          "' href='#'>Unnest Strategy</a>&nbsp;|&nbsp;";
      
      var expand_step = "<a title='" + moExp + "' class='expand_step_link " +
          disab + "' href='#'>" + oM + "</a>&nbsp;|&nbsp;";

    } else {
      // simple step
      disab = "";
      if (jsonStep.isboolean) {
        rename_step = "<a title='" + ss_rename_popup +
            "' class='rename_step_link disabled' href='javascript:void(0)'>" +
            "Rename</a>&nbsp;|&nbsp;";
      } else {
        rename_step = "<a title='" + ss_rename_popup +
            "' class='rename_step_link' href='#'>Rename</a>&nbsp;|&nbsp;";
      }

      view_step = "<a title='" + ss_view_popup + "' class='view_step_link' " +
          "href='#'>View</a>&nbsp;|&nbsp;";

      analyze_step = "<a title='" + ss_analyze_popup + "' " +
          "class='analyze_step_link' href='#'>Analyze</a>&nbsp;|&nbsp;";

      if (modelstep.isTransform || modelstep.frontId == 1) {
        hideOp = true;
      }

      if (jsonStep.isboolean) {
        hideQu = true;
        disab = "disabled";
      }

      var parms = jsonStep.urlParams;

      edit_step = "<a title='" + ss_edit_popup + "'  class='edit_step_link " +
          disab + "' href='#' id='" + sid + "|" + jsonStep.id + "|" +
          modelstep.operation + "'>Revise</a>&nbsp;|&nbsp;";

      if (modelstep.frontId == 1 || modelstep.isTransform ||
          jsonStep.isboolean) {
        expand_step = "<a title='" + ss_expand_popup +
            "' class='expand_step_link disabled' href='javascript:void(0)'> " +
            "Make Nested Strategy</a>&nbsp;|&nbsp;";
      } else {
        expand_step = "<a title='" + ss_expand_popup +
            "' class='expand_step_link' href='#' " +
            ">Make Nested Strategy</a>&nbsp;|&nbsp;";
      }
      
      collapse_step = "";
    }
    var insertRecName = (prevjsonstep == null) ? jsonStep.dataType :
        prevjsonstep.dataType;      
    var insert_step = "<a title='" + insert_popup +
        "' class='insert_step_link' id='" + sid + "|" + parentid +
        "' href='#'>Insert Step Before</a>&nbsp;|&nbsp;";
    var customMenu = "";

    // this code (function in wdkCustomization/js/customStrategy.js)  adds the ortholog link 
    try {
      if (typeof customCreateDetails === "function") {
        customMenu = customCreateDetails(jsonStep, modelstep, strat);
      }
    } catch(err) {
      if (console && console.log) {
        console.log(err);
      }
      alert("view.js: a backend error occurred.");
    }

    var delete_strat = '';

    if (modelstep.frontId == 1 && strat.nonTransformLength == 1) {
      var delete_step = "<a title='" + delete_popup +
          "' class='delete_step_link' href='#'>Delete</a>";
    } else {
      delete_step = "<a title='" + delete_popup + "' class='delete_step_link'" +
          " href='#'>Delete</a>";
    }

    if (!jsonStep.isAnalyzable) {
      analyze_step = '';
    }

    var close_button = "<a title='" + x_popup +
        "' class='close_link' href='#' " +
        "'><img src=\"" + wdk.assetsUrl('wdk/images/close.gif') + "\" /></a>";

    var inner = ""+  
        "    <div class='crumb_menu'>" + close_button + rename_step +
        view_step + analyze_step + edit_step + expand_step + collapse_step + insert_step +
        customMenu + delete_step + "    </div>"+ name +
        "    <table></table><hr class='clear' />" + filteredName +
        "    <p><b>Results:&nbsp;</b>" + jsonStep.results + "&nbsp;" +
        wdk.util.getDisplayType(jsonStep);
        // + "&nbsp;&nbsp;|&nbsp;&nbsp;<a href='downloadStep.do?step_id=" + modelstep.back_step_Id + "'>Download</a>";
         
    inner += "<hr class='clear' />" + createWeightSection(jsonStep,modelstep,sid);

    $(detail_div).html(inner);

    if (!jsonStep.isValid) {
      $(detail_div).find('.crumb_menu a')
        .not('.edit_step_link')
        .not('.delete_step_link')
        .not('.close_link')
        .not('.expand_step_link')
        .addClass('disabled');
    }
    if (jsonStep.invalidQuestion == 'true') {
      $(detail_div).find('.crumb_menu a.edit_step_link')
        .addClass('disabled');
    }

    $("table", detail_div).replaceWith(params_table);
    return detail_div;       
  }

  function createWeightSection(jsonStep,modelstep,sid) {
    // display & assign weight
    if (!jsonStep.useweights || modelstep.isTransform || jsonStep.isboolean ||
        modelstep.isSpan) {
      return "";
    }
    
    var set_weight = "<div name='All_weighting' class='param-group' " +
        "type='ShowHide'><div class='group-title'> "+
        "<img style='position:relative;top:5px;'  class='group-handle' " +
        "src='" + wdk.assetsUrl('wdk/images/plus.gif') + "'/>Give this search a weight</div>" +
        "<div class='group-detail' style='display:none;text-align:center'>"+
        "<div class='group-description'>"+
        "<p><input type='text' name='weight' value='" +
        jsonStep.assignedWeight + "'>  </p> "+
        "<input class='weight-button' type='button' value='Assign'>"+
        "<p>Optionally give this search a 'weight' (for example 10, 200, -50)." +
        "<br>In a search strategy, unions and intersects will sum the weights," +
        "giving higher scores to items found in multiple searches.</p>"+
        "</div><br></div></div>";
    return set_weight;
  }

  // HANDLE THE DISPLAY OF THE PARAMETERS IN THE STEP DETAILS BOX
  function createParameters(params, isSpan) {
    var table = document.createElement('table');
    //Note for datasetParams
    var dPmessage="<br>Review Your Results</b>: It is possible that some of " +
        "your IDs are not in our database and will not return results.<br> " +
        "Until we have a mechanism for informing you which IDs are missing, " +
        "please review your results carefully.";
      
    if (isSpan) {
      // TODO:  if span logic moves into WDK, the code
      // for the span logic details box should move here.
      try {  //params includes the sentence with formatting and all
        var contents = customSpanParameters(params);
        $(table).append(contents);
      } catch(err) {
        // no-op
      }
    } else {
      $(params).each(function() {

        if (this.visible) {
          var tr = document.createElement('tr');
          var prompt = document.createElement('td');
          var space = document.createElement('td');
          var value = document.createElement('td');
          $(prompt).addClass("medium param name");
          $(prompt).html("<b><i>" + this.prompt + "</i></b>");
          $(space).addClass("medium param");
          $(space).html("&nbsp;:&nbsp;");
          $(value).addClass("medium param value");
          $(value).html( getParamDisplay(this) );
          $(tr).append(prompt);
          $(tr).append(space);
          $(tr).append(value);
          $(table).append(tr);

          if(this.prompt.indexOf("input set") != -1) {
            tr = document.createElement('tr');
            prompt = document.createElement('td');
            prompt.setAttribute('colspan','3');
            //$(prompt).addClass("medium param name");
            $(prompt).html("<b><i>" + dPmessage + "</i></b>");
            $(tr).append(prompt);
            $(table).append(tr);
          }

        }

      });
    }
    return table;
  }

  function getParamDisplay(param) {
    if (param.className === 'org.gusdb.wdk.model.jspwrap.FilterParamBean') {
      var filters = JSON.parse(param.display);
      return _.pluck(filters, 'display').join(', <br>');
    }

    return param.display;
  }

  // HANDLE THE DISPLAY OF THE STRATEGY RECORD TYPE DIV
  function createRecordTypeName(strat) {
    if (strat.subStratOf == null) {
      var div_sn = document.createElement("div");
      $(div_sn).attr("id","record_name").addClass("strategy_small_text").text("(" + wdk.util.getDisplayType(strat.JSON.steps[strat.JSON.steps.length]) + ")");
      return div_sn;
    }
  }

  function createParentStep(strat) {
    var parentStep = null;
    var pstp = document.createElement('div');

    if (strat.subStratOf != null) {
      parentStep = strat.findParentStep(strat.backId.split("_")[1],false);
    }

    if(parentStep == null) {
      return;
    } else {
      $(pstp).attr("id","record_name");
      $(pstp).append("Expanded View of Step <i>" + strat.JSON.name + "</i>");
      return pstp;
    }
  }

  // HANDLE THE DISPLAY OF THE STRATEGY NAME DIV
  function createStrategyName(strat) {
    var json = strat.JSON;
    var id = strat.backId;
    var name = json.name;
    var append = json.saved ? "" : append = "<span title='Indicates this strategy is work in progress, it is not a snapshot; you may continue changing steps in it (aka unsaved)' class='append'>*</span>";
    var exportURL = wdk.exportBaseURL() + json.importId;
    var share = "";

    if (json.saved) {
      share = "<a id='share_" + id + "' title='Email this URL to your best friend.' " +
          "href=\"#share\"><b style='font-size:120%'>Share</b></a>";
    } else if (wdk.user.isGuest()) {
      share = "<a id='share_" + id + "' title='Please LOGIN so you can SAVE " +
          "and then SHARE (email) your strategy.' href='#share'>" +
          "<b style='font-size:120%'>Share</b></a>";
    } else {
      share = "<a id='share_" + id + "' title='SAVE this strategy so you can " +
          "SHARE it (email its URL).' href='#share'><b style='font-size:120%'>Share</b></a>";
    }

    var save = "";
    var sTitle = "Save As";
    // if(json.saved) sTitle = "COPY AS";

    if (wdk.user.isGuest()) {
      save = "<a id='save_" + id + "' title='Please LOGIN so you can SAVE " +
          "(make a snapshot) your strategy.' class='save_strat_link' " +
          "href='#save'><b style='font-size:120%'>" +
          sTitle + "</b></a>";
    } else {
      save = "<a id='save_" + id + "' title='A saved strategy is like a " +
          "snapshot, it cannot be changed.' class='save_strat_link' " +
          "href='#save'>" +
          "<b style='font-size:120%'>" + sTitle + "</b></a>";
    }
    // save += "<div id='save_strat_div_" + id + "' class='modal_div save_strat'" +
    //     " style='width:500px'>" +
    //     "<div class='dragHandle'>" +
    //     "<div class='modal_name'>"+
    //     "<span class='h3left'>" + sTitle + "</span>" + 
    //     "</div>"+ 
    //     "<a class='close_window' href='javascript:wdk.addStepPopup.closeModal()'>"+
    //     "<img alt='Close' src='" + wdk.assetsUrl('wdk/images/close.gif') + "' />" +
    //     "</a>"+
    //     "</div>"+
    //     "<form id='save_strat_form' onsubmit='return wdk.addStepPopup.validateSaveForm(this);'" +
    //     " action=\"javascript:saveStrategy('" + id + "', true)\">"+
    //     save_warning +
    //     "<input type='hidden' value='" + id + "' name='strategy'/>"+
    //     "<input type='text' value='' name='name' size ='50'/>"+
    //     "<input style='margin-left:5px;' type='submit' value='Save'/>"+
    //     "</form>"+
    //     "</div>";
    var copy = "<a title='Create a copy of the strategy.' " +
        "class='copy_strat_link' href='#copy'>" +
        "<b style='font-size:120%'>Duplicate</b></a>";

    var rename = "<a id='rename_" + strat.frontId +
        "' href='#rename' title='Click to rename.'>" +
        "<b style='font-size:120%'>Rename</b></a>";

    var deleteStrat = "<a id='delete_" + strat.frontId +
        "' href='#delete' title='Click to delete.' " +
        "><b style='font-size:120%'>Delete</b></a>";

    var descriptionText = strat.description ? 'View Description' : 'Add Description';

    var description = '<a id="description_' + strat.frontId +
      '" href="#describe" title="View description" ' +
      '><b style="font-size:120%;">' + descriptionText + '</b></a>';

    /* FIXME: First attempt at adding 'make public' link on right side of strategy display;
     *        Not called and does not work!  See also publicStrats.js, search 'togglePublicFromLink'
    var publicizeLinkText = strat.isPublic ? 'Make Private' : 'Make Public';
    var publicizeLinkTitle = strat.isPublic ?
        'No longer show this strategy in the "Public & Example" tab.' :
        'Make this strategy visible to the community in the "Public & Example" tab.';
    var publicizeStrat = "<a id='publicize_" + strat.frontId +
        "' href='javascript:void(0)' title='" + publicizeLinkTitle +
        "' onclick=\"wdk.publicStrats.togglePublicFromLink(this, '" + id +
        "')\"><b style='font-size:120%'>" + publicizeLinkText + "</b></a>";
    */
    
    var div_sn = document.createElement("div");
    var div_sm = document.createElement("div");
    var div_sm_html = '';
    $(div_sn).attr("class","strategy_name");
    $(div_sm).attr("class","strategy_menu");

    if (strat.subStratOf == null) {
    //		name="Unnamed";
      $(div_sn).html("<span title='Click to edit" +
          "'>Strategy: <span class='strategy-name wdk-editable' " +
          "data-id='" + strat.backId + "'" +
          "data-save='wdk.strategy.controller.updateStrategyName'" +
          " style='font-style:italic;cursor:pointer'>" + name + "</span></span>" + append +
          "<span id='strategy_id_span' style='display: none;'>" + id +
          "</span>");

      div_sm_html += '<span class="strategy_small_text">';

      if (strat.isSaved || strat.description) {
        div_sm_html += "<br/>" + description;
      }

      div_sm_html += "<br/>" + rename;
      div_sm_html += "<br/>" + copy;
      div_sm_html += "<br/>" + save;
      div_sm_html += "<br/>" + share;
      // div_sm_html += "<br/>" + publicizeStrat;
      div_sm_html += "<br/>" + deleteStrat;

      div_sm_html += "</span>";

      $(div_sm).html(div_sm_html);
    }//else{
      //$(div_sn).html("<span style='font-size:14px;font-weight:bold' title='Name of this substrategy. To rename, click on the corresponding step name in the parent strategy'>" + name + "</span>" + "<span id='strategy_id_span' style='display: none;'>" + id + "</span>"); 
    //}
    $(div_sm).css({'z-index' : 90}); // DO NOT DELETE, needed for IE7
    return [div_sn, div_sm];
  }

  //REMOVE ALL OF THE SUBSTRATEGIES OF A GIVEN STRATEGY FROM THE DISPLAY
  function removeStrategyDivs(stratId) {
    var strategy = modelNS.getStrategyFromBackId(stratId);

    if (strategy != null && strategy.subStratOf != null) {  //stratId.indexOf("_") > 0
      var currentDiv = $("#Strategies div#diagram_" + strategy.frontId).remove();
      var sub = modelNS.getStrategyFromBackId(stratId.split("_")[0]);  //substring(0,stratId.indexOf("_")));
      //$("#Strategies div#diagram_" + sub.frontId).remove();
      var subs = wdk.strategy.model.getSubStrategies(sub.frontId);

      for (var i = 0; i < subs.length; i++) {
        $("#Strategies div#diagram_" + subs[i].frontId).remove();
      }
    }
  }


  // DISPLAY UTILITY FUNCTIONS
  // is this deprecated? - dmf

  var offset = function(modelstep){
    if(modelstep == null){
      return leftOffset + 123;
    }
    if((modelstep.isboolean || modelstep.isSpan) && (modelstep.prevStepType == "boolean" || modelstep.prevStepType == "span")){
      leftOffset += b2b;
    }else if((modelstep.isboolean || modelstep.isSpan) && modelstep.prevStepType == "transform"){
      leftOffset += b2t;
    }else if(modelstep.isTransform && (modelstep.prevStepType == "boolean" || modelstep.prevStepType == "span")){
      leftOffset += t2b;
    }else if(modelstep.isTransform && modelstep.prevStepType == "transform"){
      leftOffset += t2t;
    }else if(modelstep.isboolean || modelstep.isSpan){
      leftOffset += f2b;
    }else if(modelstep.isTransform){
      leftOffset += f2t;
    }
    return leftOffset;
  };

  function createInvalidDiv() {
    var inval = document.createElement('div');
    has_invalid = true;
    inval.setAttribute("class","invalidStep");
    var i = document.createElement('img');
    $(i).attr("src",wdk.assetsUrl("wdk/images/InvalidStep.png"))
        .attr("style","height:36;width:98;cursor:pointer");
        //.attr("onclick","wdk.strategy.view.reviseInvalidSteps(this)");
    $(inval).append(i);
    return inval;
  }

  function createInvalidText() {
    var t = document.createElement('div');
    $(t).attr("id","invalid-step-text").attr('class','simple');
    $.ajax({
      url:"wdk/jsp/invalidText.jsp",
      dataType: "html",
      type:"get",
      async:false,
      success:function(data){
        $(t).html(data); 
      }
    });
    return t;
  }

  function closeInvalidText(ele) {
    $(ele).parent().remove();
  }

  function reviseInvalidSteps(ele) {
    var iv_id = $(ele).parent().attr("id").split("_");
    var stratDiagram = $("div#diagram_" + iv_id[0])[0];
    var stepLinkSelector = "div#step_" + iv_id[1] + "_sub a#stepId_" + iv_id[1];
    $(stratDiagram).find(stepLinkSelector).click();
    $(stratDiagram).find("div#invalid-step-text").remove();
  }

  function getSpanOperation(params) {
    var op = '';
    $(params).each(function() {
      if (this.name == 'span_operation') {
        op = this.internal;
      }
    });
    return op;
  }

  ns.displayModel = displayModel;
  ns.save_warning = save_warning;
  ns.closeInvalidText = closeInvalidText;
  ns.reviseInvalidSteps = reviseInvalidSteps;
  ns.removeStrategyDivs = removeStrategyDivs;

});
