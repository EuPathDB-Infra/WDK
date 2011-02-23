var dependedParams;
var dependedValues;
var displayTermMap;
var oldValues
var termDisplayMap;

function initParamHandlers(isPopup, isEdit) {
	dependedParams = new Array();
	displayTermMap = new Array();
	termDisplayMap = new Array();
	oldValues = new Array();

	if(isEdit == undefined) isEdit = false;
	initTypeAhead(isEdit);
	initDependentParamHandlers(isEdit);
	if (!isPopup){
		$("#form_question").submit(function() {
			mapTypeAheads();
			return true;
		});
	}
}

function initDependentParamHandlers(isEdit) {
	$('div.dependentParam').each(function() {
		$('input, select', this).attr('disabled',true);
		var name = $(this).attr('name');
		if (!dependedParams[name]) {
			dependedParams[name] = $(this).attr('dependson');
		}
		var dependedParam = $("td#" + dependedParams[name] + "aaa input[name='array(" + dependedParams[name] + ")'], td#" + dependedParams[name] + "aaa select[name='array(" + dependedParams[name] + ")']");
		dependedParam.change(function() {
			dependedValues = [];
			var paramName = getParamName($(this).attr('name'), true);
			var inputs = $("td#" + paramName + "aaa input[name='array(" + paramName + ")']:checked, td#" + paramName + "aaa select[name='array(" + paramName + ")']");
			inputs.each(function() {
				dependedValues.push($(this).val());
			});
			updateDependentParam(name, dependedValues.join(","));
		});
	});

	//If revising, store all of the old param values before triggering the depended param's change function.
	if (isEdit) {
		for (var name in dependedParams) {
			var input = $("input.typeAhead[name='value(" + name + ")']");
			if (input.length == 0) {
				input = $("div.dependentParam[name='" + name + "']").find("select");
				if (input.length > 0) {
					// If this is a select, there's only one value
					oldValues[name] = input.val();
				}
				else {
					// Otherwise, we have to know which option(s) are checked
					var allVals = [];
					$("div.dependentParam[name='" + name + "']").find("input:checked").each(function() {
       						allVals.push($(this).val());
	     				});
					oldValues[name] = allVals;
				}
			}
		}
	}

	// trigger the change function once for each depended param.
	var triggeredParams = [];
	for (var name in dependedParams) {
		if (!triggeredParams[dependedParams[name]]) {
			dependedParam =  $("td#" + dependedParams[name] + "aaa input[name='array(" + dependedParams[name] + ")']:first," +
                	                   "td#" + dependedParams[name] + "aaa select[name='array(" + dependedParams[name] + ")']");
			dependedParam.change();
			triggeredParams[dependedParams[name]] = true;
		}
	}
}

function initTypeAhead(isEdit) {
	$("input:hidden.typeAhead").each(function() {
		var questionName = $(this).closest("form").find("input:hidden[name=questionFullName]").val();
		var paramName = getParamName($(this).attr('name'));
		$("#" + paramName + "_display").attr('disabled',true);
		if (isEdit) oldValues[paramName] = $(this).val();
		if(!$(this).parent('div').hasClass('dependentParam')) {
			$("#" + paramName + "_display").val('Loading options...');
			var sendReqUrl = 'getVocab.do?questionFullName=' + questionName + '&name=' + paramName + '&xml=true';
			$.ajax({
				url: sendReqUrl,
				dataType: "xml",
				success: function(data){
					createAutoComplete(data, paramName);
				}
			});
		}
	});
}

function createAutoComplete(obj, name) {
	$("div.ac_results").remove(); // Remove any pre-existing type-ahead results.
	var def = new Array()
	displayTermMap[name] = new Array();
	termDisplayMap[name] = new Array();
	var term;
	var display;
	var value = '';
	if( $("term",obj).length != 0 ){
		$("term",obj).each(function(){
			term = this.getAttribute('id');
			display = this.firstChild.data;
			def.push(display);
			displayTermMap[name][display] = term;
			termDisplayMap[name][term] = display;
		});		
	}
	$("#" + name + "_display").unautocomplete().autocomplete(def,{
		matchContains: true
	});
	if (oldValues[name]) {
		value = termDisplayMap[name][oldValues[name]]; // Look up the display for the old value
		if (!value) value = oldValues[name]; // For typeaheads allowing arbitrary input
		oldValues[name] = null;
	}
	$("#" + name + "_display").val(value).removeAttr('disabled');
}

function updateDependentParam(paramName, dependedValue) {
	if (dependedValue && dependedValue != 'Choose one:') {
		var dependentParam = $("td#" + paramName + "aaa > div.dependentParam[name='" + paramName + "']");
		var questionName = dependentParam.closest("form").find("input:hidden[name=questionFullName]").val();
		var sendReqUrl = 'getVocab.do?questionFullName=' + questionName + '&name=' + paramName + '&dependedValue=' + dependedValue;
		if ($('input.typeAhead',dependentParam).length > 0) {
			var sendReqUrl = sendReqUrl + '&xml=true';
			$("#" + paramName + "_display").attr('disabled',true).val('Loading options...');
			$.ajax({
				url: sendReqUrl,
				dataType: "xml",
				success: function(data){
					$('input',dependentParam).removeAttr('disabled');
					createAutoComplete(data, paramName);
				}
			});
		} else {
			$.ajax({
				url: sendReqUrl,
				type: "POST",
				data: {},
				dataType: "html",
				success: function(data){
					var newContent = $("div.param, div.param-multiPick",data);
					dependentParam.html(newContent.html());
					if (oldValues[paramName]) {
						var input = $("select",dependentParam);
						if (input.length > 0) {
							input.val(oldValues[paramName]);
						}
						else {
							var allVals = oldValues[paramName];
							jQuery.each(allVals, function() {
								$("input[value=" + this + "]", dependentParam).attr('checked',true);
							});
						}
						oldValues[name] = null;
					}
				}
			});
		}
	}
}

function mapTypeAheads() {
	$("input:hidden.typeAhead").each(function() {
		var paramName = $(this).attr('name');
		paramName = getParamName(paramName);
		var newValue = displayTermMap[paramName][$("#" + paramName + "_display").val()];
		if (!newValue) newValue = $("#" + paramName + "_display").val();
		$(this).val(newValue);
	});
}

function getParamName(inputName, multiValue) {
	var paramName;
	if (multiValue) {
		paramName = inputName.match( /array\(([^\)]+)\)/ );
	}
	else {
		paramName = inputName.match( /value\(([^\)]+)\)/ );
	}
	if (paramName) return paramName[1];
}
