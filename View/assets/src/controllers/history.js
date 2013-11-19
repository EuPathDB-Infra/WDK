wdk.util.namespace("window.wdk.history", function(ns, $) {
  "use strict";

  // temp reference to wdk.strategy.model
  //var modelNS = wdk.strategy.model;

  var selected = [];
  var overStepId = 0;
  var currentStepId = 0;
  var update_hist = true;
  var queryhistloaded = false;

  /*
   * @deprecated Url return 500 status
   */
  function updateQueryHistory() {
    $.ajax({
      url: "showQueryHistory.do?type=step",
      dataType: "html",
      beforeSend:function(){
        $("body").block();
      },
      success: function(data){
        $("div.loading").html(data);
        $("body").unblock();
      }
    });
  }

  /* Create an array with the values of all the checkboxes in a column */
  /* This function is used to sort datatable columns by checkbox (checked vs. not) */
  $.fn.dataTableExt.afnSortData['dom-checkbox'] = function(oSettings, iColumn) {
    return $.map(oSettings.oApi._fnGetTrNodes(oSettings), function (tr, i) {
      return $('td:eq('+iColumn+') input', tr).prop('checked') ? '1' : '0';
    } );
  };

  function updateHistory() {
    if (update_hist) {
      update_hist = false;
      queryhistloaded = false;

      $("body").block();
      $.ajax({
        url: "showQueryHistory.do",
        dataType: "html",
        success: function(data) {
          var activeTab = document.getElementById("tab_" +
              wdk.stratTabCookie.getCurrentTabCookie('browse'));
          var active = activeTab ? $("#history-menu > ul > li").index(activeTab) : 0;

          $("#search_history").html(data)
          .find( "#history-menu" ).tabs({
            active: active,

            cache: false,

            load: function(event, ui) {
              wdk.history.selectNoneHist();
              wdk.stratTabCookie.setCurrentTabCookie('application', 'search_history');
              wdk.stratTabCookie.setCurrentTabCookie('browse', ui.tab.data("name"));

              ui.panel.find("table.datatables").dataTable({
                "bAutoWidth": false,
                "bJQueryUI": true,
                "bScrollCollapse": true,
                "aoColumns": [ { "bSortable": false },
                               null, 
                               { "bSortable": false },
                               { "bSortable": false },
                               null,
                               null,
                               null,
                               null,
                               { "sSortDataType": "dom-checkbox" } ],
                "aaSorting": [[ 6, "desc" ]]
              });
              ui.panel.removeClass("ui-widget ui-widget-content");
              wdk.load();
            }
          }).removeClass("ui-widget ui-widget-content");

          $("#strategy_tabs li a#tab_search_history font.subscriptCount")
              .html("(" + $("#search_history span#totalStrategyCount").html() +
                  ")");
          //initDisplayType(); // deprecated
          $("body").unblock();
        },
        error: function(data, msg, e) {
          $("body").unblock();
          alert("ERROR \n "+ msg + "\n" + e +
              ". \nReloading this page might solve the problem.\n" +
              "Otherwise, please contact site support.");
        }
      });

    } else {
      //initDisplayType(); // deprecated
    }
  }

  /*
   * @deprecated Uses jQuery UI tab API
   */
  function initDisplayType() {
    var currentPanel = wdk.stratTabCookie.getCurrentTabCookie('browse');

    if ($("#search_history #history-menu > ul").length > 0) {
      if (currentPanel == undefined ||
          $("#search_history .menubar #tab_" + currentPanel).length == 0) {
        var typeTab = $("#search_history .menubar a:first");

        if (typeTab.length > 0) {
          var type = typeTab.attr("id").substr(4);
          displayHist(type);
        }

      } else {
        displayHist(currentPanel);
      }
    }
  }

  /*
   * @deprecated
   */
  function toggleSteps(strat) {
    var img = $("img#img_" + strat);

    if (img.hasClass("plus")) {
      $("tbody#steps_" + strat + " tr").each(function() {
        this.style.display = "";
      });
      img[0].src = wdk.assetsUrl("/wdk/images/sqr_bullet_minus.png");
      img.removeClass("plus");
      img.addClass("minus");
    } else {
      $("tbody#steps_" + strat + " tr").each(function() {
        this.style.display = "none";
      });
      img[0].src = wdk.assetsUrl("/wdk/images/sqr_bullet_plus.png");
      img.removeClass("minus");
      img.addClass("plus");
    }
  }

  /*
   * @deprecated
   */
  function toggleSteps2(strat) {
    var img = $("img#img_" + strat);

    if (img.hasClass("plus")) {
      $("tr#desc_" + strat + " tr").each(function() {
        this.style.display = "";
      });
      $("tr#desc_" + strat).each(function() {
        this.style.display = "";
      });
      img[0].src = wdk.assetsUrl("/wdk/images/sqr_bullet_minus.png");
      img.removeClass("plus");
      img.addClass("minus");

    } else {
      $("tr#desc_" + strat + " tr").each(function() {
        this.style.display = "none";
      });
      $("tr#desc_" + strat).each(function() {
        this.style.display = "none";
      });
      img[0].src = wdk.assetsUrl("/wdk/images/sqr_bullet_plus.png");
      img.removeClass("minus");
      img.addClass("plus");
    }
  }

  /*
   * @deprecated
   */
  function showHistSave(ele, stratId, save,share) {
    var perm_popup = $("div#hist_save_rename");
    var stratName = $("div#text_" + stratId + " span").text();
    var popup = perm_popup.clone();
    $(".viewed-popup-box").remove();
    popup.addClass('viewed-popup-box');
    $("input[name='name']", popup).attr("value",stratName).attr("size",40);
    $("input[name='strategy']",popup).attr("value",stratId);

    if (save) {
      $("form#save_strat_form_hist", popup).attr("action",
          "javascript:wdk.strategy.controller.saveOrRenameStrategy(" +
          stratId + ", true, true, true)");
      $("span.h3left", popup).text("Save As");
      $("input[type=submit]", popup).attr("value", "Save");

      if (share) {
        $("span.h3left", popup).text("First you need to Save it!");
      }

    } else {
      $("form#save_strat_form_hist", popup).attr("action",
          "javascript:wdk.strategy.controller.saveOrRenameStrategy(" +
          stratId + ", true, false, true)");
      $("span.h3left", popup).text("Rename");
      $("input[type=submit]", popup).attr("value", "Rename");
    }

    var btnOffset = $(ele).offset();
    var prntOffset = $("div#search_history").offset();
    popup.css("top", (btnOffset.top - prntOffset.top - 40) + "px");
    popup.css("right", "292px");
    popup.appendTo(perm_popup.parent()).show();

    $("input[name='name']", popup).focus().select();

    if (save) {
      $("form#save_strat_form_hist i").css("display","block");
    } else {
      $("form#save_strat_form_hist i").css("display","none");
    }
  }

  function showHistShare(ele, stratId, url) {
    var dialog_container = $("#wdk-dialog-share-strat");

    dialog_container.dialog("open")
    .find(".download").one("click", function(e) {
      e.preventDefault();
      downloadStep($(ele).parents(".strategy-data").data("step-id"));
    });

    $("<input/>").val(url)
        .appendTo(dialog_container.find(".share_url").html(""))
        .focus()
        .select();
  }

  function selectAllHist(type) {
    var currentPanel = wdk.stratTabCookie.getCurrentTabCookie('browse');
    selectNoneHist();

    if (type == 'saved') {
      $("div.history_panel.saved-strategies.panel_" + currentPanel +
          " input.strat-selector-box:checkbox").attr("checked", "yes");

    } else if (type == 'unsaved') {
      $("div.history_panel.unsaved-strategies.panel_" + currentPanel +
          " input.strat-selector-box:checkbox").attr("checked", "yes");

    } else {
      $("div.history_panel.panel_" + currentPanel + " input:checkbox.strat-selector-box")
          .attr("checked", "yes");
    }

    updateSelectedList();
  }

  function selectNoneHist() {
    $("div.history_panel input:checkbox.strat-selector-box").removeAttr("checked");
    selected = [];
  }

  /*
   * @deprecated - uses jQuery tab API now
   */
  function displayHist(type) {
    $("#search_history .menubar .selected_type").removeClass("selected_type");
    $(".history_panel").hide();
    selectNoneHist();

    $("#search_history .menubar li").each(function() {
      var id = $("a", this).attr("id");

      if (id == 'tab_' + type) {
        $(this).addClass("selected_type");
      }
    });

    // deprecated - was for everything
    if (type == 'cmplt') {
      if (!queryhistloaded) {
        updateQueryHistory();
        queryhistloaded = true;
      }
      $(".history_controls").hide();

    } else {
      $(".history_controls").show();
    }

    $("div.panel_" + type).show();

    if ($("div.panel_" + type + " .unsaved-strategies-body").height() > 250) {
      $("div.panel_" + type + " .unsaved-strategies-body")
          .addClass('tbody-overflow');
    }

    wdk.stratTabCookie.setCurrentTabCookie('application', 'search_history');
    wdk.stratTabCookie.setCurrentTabCookie('browse', type);
  }

  function updateSelectedList() {
    selected = [];

    $("div.history_panel input:checkbox.strat-selector-box").each(function (i) {
      if ($(this).attr("checked")) {
        selected.push($(this).attr("id"));
      }
    });
  }
    
  function downloadStep(stepId) {
    var url = "downloadStep.do?step_id=" + stepId;
    window.location = url;
  }

  function handleBulkStrategies(type, stratToDelete) {
    //this function is called from two different locations in the page:
    //  - the button, expecting at least one selected strategy, or 
    //  - from the dropdown menu, specific to each strategy. (was calling deleteStrategy() in controller-JSON.js)
    // In the latter case, there is no need to select; the click tells us which strat is to be deleted (stratToDelete, std)

    var std = stratToDelete;

    if (!std && selected.length == 0) {
      alert("No strategies were selected!");
      return false;
    }

    if (type == 'delete') {
      var stratNames = '<ol>';
      $.each(selected, function(i, n) {
        stratNames += "<li>" + $.trim($("div#text_" + n).text())+ "</li>";
      });
      stratNames += '</ol>';

      if (std) {
        stratNames = $.trim($("div#text_" + std).text());
      } else {
        std = 0;
      }

      $.blockUI({
        message: "<h2>Delete Strategies</h2><span style='font-weight:bold'>" +
            "You are about to delete the following strategies:</span><br />" +
            stratNames + "<br /><br />If you shared a strategy, its URL will " +
            "stay valid.<br /><br /><span style='font-weight:bold'>" +
            "Are you sure you want to do that?</span><br/>" +
            "<form action='javascript:wdk.history.performBulkAction(\"" + type + "\",\"" +
            std + "\");'><input type='submit' onclick='jQuery.unblockUI();" +
            "return false;' value='Cancel'/><input type='submit' " +
            "onclick='jQuery.unblockUI();return true;' value='OK' /></form>",
        css: {
          position: 'absolute',
          backgroundImage: 'none'
        }
      });

    } else {
      performBulkAction(type);
    }

    std = 0;
  }

  function performBulkAction(type, stratTD) {
    var agree;
    var url;

    if (type == 'delete') {
      url = "deleteStrategy.do?strategy=";
    } else if (type == 'open') {
      url = "showStrategy.do?strategy=";
    } else {
      url = "closeStrategy.do?strategy=";
    }

    if (stratTD > 0) {
      url = url + stratTD;
    } else {
      url = url + selected.join(",");
    }

    $.ajax({
      url: url,
      dataType: "json",
      data:"state=" + wdk.strategy.controller.stateString,
      success: function(data) {
        selectNoneHist();
        wdk.strategy.controller.updateStrategies(data);
        if (type == 'open') {
          wdk.addStepPopup.showPanel('strategy_results');
        } else{
          update_hist = true;
          updateHistory(); // update history immediately, since we're already on the history page
        }
      },
      error: function(data, msg, e) {
        selectNoneHist();
        $("div#search_history").unblock();
        alert("ERROR \n " + msg + "\n" + e + ".\n" +
            "Reloading this page might solve the problem.\n" +
            "Otherwise, please contact site support.");
      }
    });

    stratTD = undefined;
  }

  /*
   * @deprecated No references
   */
  function displayName(histId) {
    if (overStepId != histId) {
      hideAnyName();
    }

    overStepId = histId;
    var display = $('#div_' + histId);
    display.css({ 'top' : (display.parent().position().top + 20)});
    $('#div_' + histId).show();
  }

  /*
   * @deprecated No references
   */
  function hideName(histId) {
    if (overStepId == 0) return;
    $('#div_' + histId).hide();
  }

  /*
   * @deprecated No references
   */
  function hideAnyName() {
    hideName(overStepId);
  }

  function showDescriptionDialog(el, save, fromHist, canEdit) {
    var dialog_container = $("#wdk-dialog-strat-desc"),
        row = $(el).parents("tr"),
        strat = row.data();

    dialog_container.find(".description").html(
      strat.description
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/(https?:\/\/[^\s<]+)\.?/g, "<a href='$1' target='_blank'>$1</a>")
          .replace(/\n/g, "<br/>")
    );
    
    dialog_container.dialog("option", "title", strat.name);
    dialog_container.dialog("option", "width", 600);

    if (canEdit) {
      dialog_container.find(".edit a").click(function(e) {
        e.preventDefault();
        dialog_container.dialog("close");
        showUpdateDialog(el, save, fromHist, strat.isPublic);
        $(this).unbind("click");
      }).show();
    } else {
      dialog_container.find(".edit a").hide();
    }
    dialog_container.dialog("open");
  }

  // note checkPublic is an optional param, default value false
  function showUpdateDialog(el, save, fromHist, checkPublic) {
    var row = $(el).parents(".strategy-data"),
        strat = row.data(),
        dialog_container = $("#wdk-dialog-update-strat"),
        title = (save) ? "Save Strategy" : "Update Strategy",
        submitValue = (save) ? "Save strategy" : "Update strategy",
        type = (save) ? "SaveStrategy" : "RenameStrategy",
        form;

    dialog_container.dialog("option", "title", title)
      .find(".download").click(function(e) {
        e.preventDefault();
        downloadStep(strat.stepId);
      });

    form = dialog_container.find("form").get(0);
    $(form).unbind("submit");

    // if passed checkPublic value is defined, use it; otherwise, use value from current strategy
    var publicCheckedInForm = (typeof checkPublic === 'undefined' ? strat.isPublic : checkPublic);
    dialog_container.find(".public_input input").prop('checked', publicCheckedInForm);
    dialog_container.find(".desc-requirement").html(publicCheckedInForm ? 'required' : 'optional');
    dialog_container.find(".public_input input").change(function() {
      var isPublicChecked = $(this).prop('checked');
      dialog_container.find(".desc-requirement").html(isPublicChecked ? 'required' : 'optional');
    });

    // disable checkbox if strategy is invalid
    if (strat.valid) {
      dialog_container.find(".public_input input").removeAttr("disabled");
    } else {
      dialog_container.find(".public_input input").attr('disabled','disabled');
    }
    
    if (save) {
      dialog_container.find(".save_as_msg").show();
    } else {
      dialog_container.find(".save_as_msg").hide();
    }

    if (!(save || strat.saved)) {
      dialog_container.find(".desc_label").hide();
      dialog_container.find(".desc_input").hide();
      dialog_container.find(".public_label").hide();
      dialog_container.find(".public_input").hide();
    } else {
      dialog_container.find(".desc_label").show();
      dialog_container.find(".desc_input").show();
      dialog_container.find(".public_label").show();
      dialog_container.find(".public_input").show();
    }

    form.name.value = strat.name||"";
    form.description.value = strat.description||"";
    form.strategy.value = strat.backId;
    form.submit.value = submitValue;
    $(form).data("strategy", strat);

    $(form).submit(function(event) {
      var strategy;
      event.preventDefault();

      if (this.description.value.length > 4000) {
        alert("You have exceeded the 4,000 character limit. " +
            "Please revised your description.");
        return false;
      }
      if ($(this.is_public).prop('checked') && this.description.value.trim().length == 0) {
        alert(wdk.publicStrats.publicStratDescriptionWarning);
        return false;
      }

      dialog_container.block();
      // update strat row data
      row.data("name", this.name.value);
      row.data("description", this.description.value);
      row.data("isPublic", ($(this.is_public).prop('checked') ? "true" : "false"));

      strategy = $.extend(wdk.strategy.model.getStrategyOBJ(strat.backId), strat);

      wdk.strategy.controller.saveOrRenameStrategy(strategy, true, save,
          fromHist, form).success(function() {
        dialog_container.dialog('close');
      }).complete(function() {
        dialog_container.unblock();
      });
    });

    dialog_container.dialog('open');
  }
  
  ns.update_hist = function(bool) {
    if (typeof bool !== "undefined") {
      update_hist = Boolean(bool);
    }
    return update_hist;
  };

  ns.updateQueryHistory = updateQueryHistory;
  ns.updateHistory = updateHistory;
  ns.initDisplayType = initDisplayType;
  ns.toggleSteps = toggleSteps;
  ns.toggleSteps2 = toggleSteps2;
  ns.showHistSave = showHistSave;
  ns.showHistShare = showHistShare;
  ns.selectAllHist = selectAllHist;
  ns.selectNoneHist = selectNoneHist;
  ns.displayHist = displayHist;
  ns.updateSelectedList = updateSelectedList;
  ns.downloadStep = downloadStep;
  ns.handleBulkStrategies = handleBulkStrategies;
  ns.performBulkAction = performBulkAction;
  ns.displayName = displayName;
  ns.hideName = hideName;
  ns.hideAnyName = hideAnyName;
  ns.showDescriptionDialog = showDescriptionDialog;
  ns.showUpdateDialog = showUpdateDialog;

});
