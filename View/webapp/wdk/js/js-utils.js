function getStrategyJSON(backId){
	var strategyJSON = null;
	$.ajax({
		async: false,
		url:"showStrategy.do?strategy=" + backId + "&open=false",
		type: "POST",
		dataType: "json",
		data:"pstate=" + p_state,
		success: function(data){
			for(var s in data.strategies){
				if(s != "length") {
					data.strategies[s].checksum = s;
					strategyJSON = data.strategies[s];
				}
					
			}
		}
	});
	return strategyJSON;
}

function getStrategyOBJ(backId){
	if(getStrategyFromBackId(backId) != false){
		return getStrategyFromBackId(backId);
	}else{
		var json = getStrategyJSON(backId);
		var s = new Strategy(strats.length, json.id, false);
		s.checksum = json.checksum;
		s.JSON = json;
		s.name = json.name;
		return s;
	}
}

//show the loading icon in the upper right corner of the strategy that is being operated on
function showLoading(divId){
	var d = null;
	var l = 0;
	var t = 0;
	if(divId == undefined){
		d = $("#Strategies");
		le = "225px";
		t = "40px";
		l_gif = "loading.gif";
		sz = "45";
	}else if($("#diagram_" + divId).length > 0){
		d = $("#diagram_" + divId);
		le = "10px";
		t = "10px";
		l_gif = "loading2.gif";
		sz = "35";
	} else {
		d = $("#" + divId);
		le = "405px";
		t = "160px";
		l_gif = "loading.gif";
		sz = "50";
	}
	var l = document.createElement('span');
	$(l).attr("id","loadingGIF");
	var i = document.createElement('img');
	$(i).attr("src","wdk/images/" + l_gif);
	$(i).attr("height",sz);
	$(i).attr("width",sz);
	$(l).prepend(i);
	$(l).css({
		"text-align": "center",
		position: "absolute",
		left: le,
		top: t
	});
	$(d).append(l);
}

// remove the loading icon for the given strategy
function removeLoading(divId){
	if(divId == undefined)
		$("#Strategies span#loadingGIF").remove();
	else
		$("#diagram_" + divId + " span#loadingGIF").remove();
}

// parses the inputs of the question form to be sent via ajax call
function parseInputs(){
	var quesForm = $("#query_form form#form_question[name='wizardForm']");
	if(quesForm.length == 0) 
		quesForm = $("form#form_question[name='questionForm']");

        // Jerric - use ajax to serialize the form data
	var d = quesForm.serialize();
        return d;
}

function checkEnter(ele,evt){
	var charCode = (evt.which) ? evt.which : evt.keyCode;
	if(charCode == 13) $(ele).blur();
}

function parseUrlUtil(name,url){
 	name = name.replace(/[\[]/,"\\\[").replace(/[\]]/,"\\\]");
 	var regexS = "[\\?&]"+name+"=([^&#]*)";
 	var regex = new RegExp( regexS,"g" );
 	var res = new Array();
 	var results = regex.exec( url );
 	if( results != null )
 		res.push(results[1]);
  	if(res.length == 0)
 		return "";
 	else
 		return res;
}


function getDisplayType(type, number){
	if(sz == 1) {
		return type;
	} else if (type.charAt(type.length-1) === 'y') {
		return type.replace(/y$/,'ies');
	} else {
		return type + 's';
	}
}

function initShowHide(details){
	var wdk = new WDK();
	$(".param-group[type='ShowHide']",details).each(function() {
        // register the click event
        var name = $(this).attr("name") + "_details";
        var expire = 365;   // in days
        $(this).find(".group-handle").unbind('click').click(function() {
            var handle = this;
            var path = handle.src.substr(0, handle.src.lastIndexOf("/"));
            var detail = $(this).parents(".param-group").children(".group-detail");
            detail.toggle();
            if (detail.css("display") == "none") {
                handle.src = path + "/plus.gif";
                wdk.createCookie(name, "hide", expire);
            } else {
                handle.src = path + "/minus.gif";
                wdk.createCookie(name, "show", expire);
            }
        });

		// decide whether need to change the display or not
        var showFlag = wdk.readCookie(name);
        if (showFlag == null) return;
        
        var status = $(this).children(".group-detail").css("display");
        if ((showFlag == "show") && (status == "none")) {   
            // should show, but it is hidden
            $(this).find(".group-handle").trigger("click");
        } else if ((showFlag == "hide") && (status != "none")) {
            // should hide, bit it is shown
            $(this).find(".group-handle").trigger("click");
        }
	});
}

function setFrontAction(action, strat, step) {
	jQuery("#loginForm form[name=loginForm]").append("<input type='hidden' name='action' value='" + action + "'/>");
	jQuery("#loginForm form[name=loginForm]").append("<input type='hidden' name='actionStrat' value='" + strat + "'/>");
	jQuery("#loginForm form[name=loginForm]").append("<input type='hidden' name='actionStep' value='" + step + "'/>");
}

function setDraggable(e, handle){
	var rlimit = $("div#contentwrapper").width() - e.width() - 18;
	if(rlimit < 0) rlimit = 525;
	var blimit = $("body").height();
	$(e).draggable({
		handle: handle,
		containment: [0,0,rlimit,blimit]
	});
}

function popLogin() {
	jQuery.blockUI({message: '<h1>You have to be logged in to do that!</h1><input type="button" value="OK" onclick="$.unblockUI();" />'});
}
