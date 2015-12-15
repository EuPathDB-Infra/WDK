
//**************************************************
// Dispatcher
//**************************************************

// use the raw Flux dispatcher
var Dispatcher = Flux.Dispatcher;

// types of actions sent through the dispatcher
var ActionType = {
  CHANGE_QUESTION_ACTION: "changeQuestionAction",
  CHANGE_PARAM_ACTION:    "changeParamAction",
  CHANGE_PAGING_ACTION:   "changePagingAction",
  CHANGE_RESULTS_ACTION:  "changeResultsAction",
  SET_LOADING_ACTION:     "setLoadingAction"
};

//**************************************************
// Helper functions
//**************************************************

var Util = (function() {

  // public methods
  var exports = {
    isPositiveInteger: isPositiveInteger,
    getAnswerRequestJson: getAnswerRequestJson
  };

  function isPositiveInteger(str) {
    return /^([1-9]\d*)$/.test(str);
  }

  function getAnswerRequestJson(questionName, paramMap, pagination) {
    var paramPack = Object.keys(paramMap).map(function(paramName) {
      var param = paramMap[paramName];
      return { name: param.name, value: param.value };
    });
    var offset = (pagination.pageNum - 1) * pagination.pageSize;
    var numRecords = pagination.pageSize;
    if (offset < 0) offset = 0;
    if (numRecords < 1) numRecords = 10;
    return {
      questionDefinition: {
        questionName: questionName,
        params: paramPack,
        filters: []
      },
      formatting: {
        formatConfig: {
          pagination: { offset: offset, numRecords: numRecords },
          //attributes: null,
          //sorting: null
        }
      }
    };
  }

  return exports;

})();

//**************************************************
// Action-Creator functions interact with the server
//**************************************************

var ActionCreator = function(serviceUrl, dispatcher) {

  // public methods
  var exports = {
    setQuestion: setQuestion,
    setParamValue: setParamValue,
    setPagination: setPagination,
    loadResults: loadResults
  };

  // private data
  var _serviceUrl = serviceUrl;
  var _dispatcher = dispatcher;

  function setLoading(loading) {
    _dispatcher.dispatch({ actionType: ActionType.SET_LOADING_ACTION, data: loading });
  }

  function setQuestion(questionName) {
    var action = {
      actionType: ActionType.CHANGE_QUESTION_ACTION,
      data: {
        questionName: questionName,
        params: []
      }
    };
    // no need to load params when user selects none
    if (questionName == Store.NO_QUESTION_SELECTED) {
      _dispatcher.dispatch(action);
    }
    else {
      jQuery.ajax({
        type: "GET",
        url: _serviceUrl + "/question/" + questionName + "?expandParams=true",
        success: function(data, textStatus, jqXHR) {
          action.data = data;
          _dispatcher.dispatch(action);
        },
        error: function(jqXHR, textStatus, errorThrown ) {
          alert("Error: Unable to load params for question " + questionName);
        }
      });
    }
  }

  function setParamValue(paramName, value) {
    _dispatcher.dispatch({
      actionType: ActionType.CHANGE_PARAM_ACTION,
      data: { paramName: paramName, value: value }
    });
  }

  function setPagination(newPagination) {
    _dispatcher.dispatch({
      actionType: ActionType.CHANGE_PAGING_ACTION,
      data: newPagination
    });
  }

  function loadResults(data) {
    setLoading(true);
    jQuery.ajax({
      type: "POST",
      url: _serviceUrl + "/answer",
      contentType: 'application/json; charset=UTF-8',
      data: JSON.stringify(Util.getAnswerRequestJson(data.selectedQuestion, data.paramValues, data.pagination)),
      dataType: "json",
      success: function(data, textStatus, jqXHR) {
        setLoading(false);
        _dispatcher.dispatch({ actionType: ActionType.CHANGE_RESULTS_ACTION, data: data });
      },
      error: function(jqXHR, textStatus, errorThrown ) {
        // TODO: dispatch a CHANGE_RESULTS_ACTION with the specific error (i.e. probably user input problem)
        setLoading(false);
        alert("Error: Unable to load results");
      }
    });
  }

  return exports;
}

