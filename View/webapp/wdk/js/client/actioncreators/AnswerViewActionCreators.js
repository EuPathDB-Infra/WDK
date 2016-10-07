import {pick} from 'lodash';
// Action types
export let actionTypes = {
  ANSWER_ADDED: 'answer/added',
  ANSWER_CHANGE_ATTRIBUTES: 'answer/attributes-changed',
  ANSWER_SORTING_UPDATED: 'answer/sorting-updated',
  ANSWER_LOADING: 'answer/loading',
  ANSWER_MOVE_COLUMN: 'answer/column-moved',
  ANSWER_UPDATE_FILTER: 'answer/filtered',
  APP_ERROR: 'answer/error'
};

let hasUrlSegment = (urlSegment) => (e) => e.urlSegment === urlSegment;

/**
 * Retrieve's an Answer resource from the WDK REST Service and dispatches an
 * action with the resource. This uses the restAction helper function
 * (see ../filters/restFilter).
 *
 * Request data format, POSTed to service:
 *
 *     {
 *       "questionDefinition": {
 *         "questionName": String,
 *         "parameters": Object (map of paramName -> paramValue),
 *         "filters": [ {
 *           “name": String, value: Any
 *         } ],
 *         "viewFilters": [ {
 *           “name": String, value: Any
 *         } ]
 *       },
 *       formatting: {
 *         formatConfig: {
 *           pagination: { offset: Number, numRecords: Number },
 *           attributes: [ attributeName: String ],
 *           sorting: [ { attributeName: String, direction: Enum[ASC,DESC] } ]
 *         }
 *       }
 *     }
 *
 * @param {string} questionUrlSegment
 * @param {Object} opts Addition data to include in request.
 * @param {Array<Object>} opts.parameters Array of param spec objects: { name: string; value: any }
 * @param {Array<Object>} opts.filters Array of filter spec objects: { name: string; value: any }
 * @param {Array<Object>} opts.viewFilters Array of view filter  spec objects: { name: string; value: any }
 * @param {string} opts.displayInfo.customName Custom name for the question to display on the page
 * @param {Object} opts.displayInfo.pagination Pagination specification.
 * @param {number} opts.displayInfo.pagination.offset 0-based index for first record.
 * @param {number} opts.displayInfo.pagination.numRecord The number of records to include.
 * @param {Array<string>} opts.displayInfo.attributes Array of attribute names to include.
 * @param {Array<Object>} opts.displayInfo.sorting Array of sorting spec objects: { attributeName: string; direction: "ASC" | "DESC" }
 */
export function loadAnswer(questionUrlSegment, recordClassUrlSegment, opts = {}) {
  return function run(dispatch, { wdkService }) {
    let { parameters = {}, filters = [], displayInfo } = opts;

    // FIXME Set attributes to whatever we're sorting on. This is required by
    // the service, but it doesn't appear to have any effect at this time. We
    // should be passing the attribute in based on info from the RecordClass.
    displayInfo.attributes = "__DISPLAYABLE_ATTRIBUTES__"; // special string for all displayable attributes
    displayInfo.tables = "__DISPLAYABLE_TABLES__";         // special string for all displayable tables

    dispatch({ type: actionTypes.ANSWER_LOADING });

    let questionPromise = wdkService.findQuestion(hasUrlSegment(questionUrlSegment));
    let recordClassPromise = wdkService.findRecordClass(hasUrlSegment(recordClassUrlSegment));
    let answerPromise = questionPromise.then(question => {
      // Build XHR request data for '/answer'
      let questionDefinition = {
        questionName: question.name,
        parameters: pick(parameters, question.parameters),
        filters
      };
      let formatting = {
        formatConfig: pick(displayInfo,
          [ 'pagination', 'attributes', 'sorting' ])
      };
      return wdkService.getAnswer(questionDefinition, formatting);
    });

    return Promise.all([ answerPromise, questionPromise, recordClassPromise ])
    .then(([ answer, question, recordClass]) => {
      return dispatch({
        type: actionTypes.ANSWER_ADDED,
        payload: {
          answer,
          question,
          recordClass,
          displayInfo,
          parameters
        }
      })
    }, error => {
      dispatch({
        type: actionTypes.APP_ERROR,
        payload: { error }
      });
      throw error;
    });
  }
}

/**
 * Sort the current answer in `state` with the provided `attribute` and `direction`.
 *
 * @param {Object} state AnswerViewStore state
 * @param {Object} attribute Record attribute field
 * @param {string} direction Can be 'ASC' or 'DESC'
 */
export function sort(sorting) {
  return {
    type: actionTypes.ANSWER_SORTING_UPDATED,
    payload: { sorting }
  };
}

/**
 * Change the position of a column in the answer table.
 *
 * @param {string} columnName The name of the attribute to move.
 * @param {number} newPosition The new 0-based index position of the attribute.
 */
export function moveColumn(columnName, newPosition) {
  return {
    type: actionTypes.ANSWER_MOVE_COLUMN,
    payload: { columnName, newPosition }
  };
}

/**
 * Update the set of visible attributes in the answer table.
 *
 * @param {Array<Object>} attributes The new set of attributes to show in the table.
 */
export function changeAttributes(attributes) {
  return {
    type: actionTypes.ANSWER_CHANGE_ATTRIBUTES,
    payload: { attributes }
  };
}

/**
 * Set the filter for the answer table.
 *
 * FIXME use a service object to filter the answer.
 *
 * @param {Object} spec The filter specification.
 * @param {string} spec.terms The string to parse and filter.
 * @param {Array<string>} spec.attributes The set of attribute names whose values should be queried.
 * @param {Array<string>} spec.tables The set of table names whose values should be queried.
 */
export function updateFilter(terms, attributes, tables) {
  return {
    type: actionTypes.ANSWER_UPDATE_FILTER,
    payload: { terms, attributes, tables }
  };
}
