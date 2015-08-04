// Import modules
import React from 'react/addons';
import Loading from './Loading';
import Answer from './Answer';
import Doc from './Doc';
import ContextMixin from '../utils/contextMixin';
import combineStores from '../utils/combineStores';
import wrappable from '../utils/wrappable';


/**
 * wrap - Wrap `value` in array.
 *
 * If `value` is undefined, return an empty aray.
 * Else, if `value` is an array, return it.
 * Otherwise, return `[value]`.
 *
 * @param  {any} value The value to wrap
 * @return {array}
 */
function wrap(value) {
  if (typeof value === 'undefined') return [];
  if (!Array.isArray(value)) return [ value ];
  return value;
}

function maybeJoin(arr, delimeter) {
  if (arr !== undefined) return arr.join(delimeter);
}

function maybeSplit(str, delimeter) {
  if (str !== undefined) return str.split(delimeter);
}

// See http://facebook.github.io/react/docs/update.html
let update = React.addons.update;

// Answer is a React component which acts as a Controller-View, as well as
// a Route Handler.
//
// A Controller-View is a Flux concept in which a component can register a
// callback function with one or more Stores, and can call ActionCreators. The
// primary role of the Controller-View is to set up some state for a tree of
// React components. The Controller-View will pass objects to child components
// as `props`. Typically, these child components are reusable in the sense that
// they do not communcate directly with Stores; communication with these
// components happens solely through `props`, either as data or as callbacks.
//
//
// A Route Handler is rendered when the URL matches a route rule. The route
// rules are defined in ../routes.js. Each Route is assigned a Handler, which
// is simply a React component. The route rules are processed at three distinct
// times:
//
//   1. When the webpage is initially loaded.
//   2. When the URL is changed due to some user interaction (e.g., clicking a
//      link).
//   3. When the route is manually transitioned to, via react-router methods.
//      In this component, we make use of this.replaceWith, which is provided
//      by the Router.Navigation mixin. This is similar forwarding one action
//      to another based on some set of criteria, such as forwarding to a login
//      screen for authentication. When this happens, the URL will also change.
//
// The router is initialized in the file ../main.js with something like:
//
//     Router.run(appRoutes, function handleRoute(Handler, state) {
//       React.render( <Handler {...state}/>, document.body);
//     });
//
// The first argument to Router.run is the set of route rules. The second
// argument is a function which is called everytime route rules are processed.
// This function will render `Handler`, which is the component registered for
// the matched route, and it will pass `state` as a set of props (`this.props`
// in this component). This component is one such Handler.
//
// See https://github.com/rackt/react-router/blob/master/docs/api/run.md#callbackhandler-state
//
//
// The primary responsibility of the Answer component is to call an
// ActionCreator to load the Answer resource from the REST service, and to
// update the page when the state of the AnswerStore has changed. The Answer
// component will determine what Answer to load based on the URL parameters.

// Define the React Component class.
// See http://facebook.github.io/react/docs/top-level-api.html#react.createclass
let AnswerController = React.createClass({


  // Declare context properties used by this component. The context object is
  // defined in AppController (the application root component). React uses
  // `contextTypes` to determine which properties to add to `this.context`.
  mixins: [ ContextMixin ],

  // When the component first mounts, fetch the answer.
  componentWillMount() {
    this.sortingPreferenceKey = 'sorting::' + this.props.params.questionName;
    this.subscribeToStores();
    this.fetchAnswer(this.props);
  },

  // This is called anytime the component gets new props, just before they are
  // actually passed to the component instance. In our case, this is when any
  // part of the URL changes. We will first check if a new answer resource needs
  // to be fetched. If not, then we will check if the filter needs to be updated.
  componentWillReceiveProps(nextProps) {
    // current query and params
    let { query, params } = this.props;

    // incoming query and params
    let { query: nextQuery, params: nextParams } = nextProps;

    // update sortingPreferenceKey value
    this.sortingPreferenceKey = 'sorting::' + nextParams.questionName;

    // query keys to compare to determine if we need to fetch a new answer
    let answerQueryKeys = [ 'sortBy', 'sortDir', 'numrecs', 'offset' ];
    let filterQueryKeys = [ 'q', 'attrs', 'tables' ];

    // determine if we need to fetch a new answer
    let answerWillChange = answerQueryKeys.reduce(function(willChange, key) {
      return willChange || query[key] !== nextQuery[key];
    }, false);

    answerWillChange = answerWillChange || params.questionName !== nextParams.questionName;

    let filterWillChange = filterQueryKeys.reduce(function(willChange, key) {
      return willChange || query[key] !== nextQuery[key];
    }, false);

    // fetch answer if the query has changed, or if the question name has changed
    if (answerWillChange) {
      this.fetchAnswer(nextProps);
    }

    // filter answer if the filter terms have changed
    else if (filterWillChange) {
      let filterOpts = {
        questionName: nextParams.questionName,
        terms: nextQuery.q,
        attributes: maybeSplit(nextQuery.attrs, ','),
        tables: maybeSplit(nextQuery.tables, ',')
      };
      this.context.actions.answerActions.updateFilter(filterOpts);
    }

  },


  componentWillUnmount() {
    this.disposeSubscriptions();
  },

  // Create subscriptions to stores.
  subscribeToStores() {
    let { questionName } = this.props.params;
    let { answerStore, questionStore, recordClassStore } = this.context.stores;

    this.subscription = combineStores(
      answerStore,
      questionStore,
      recordClassStore,
      (aState, qState, rState) => {
        let answer = aState.answers[questionName];
        let { isLoading } = aState;
        let { displayInfo } = aState;
        let { filterTerm } = aState;
        let { filterAttributes = [] } = aState;
        let { filterTables = [] } = aState;
        let { filteredRecords } = aState;
        let { questions } = qState;
        let { recordClasses } = rState;

        this.setState({
          isLoading,
          answer,
          displayInfo,
          filterTerm,
          filterAttributes,
          filterTables,
          filteredRecords,
          questions,
          recordClasses
        });
      }
    );
  },


  disposeSubscriptions() {
    this.subscription.dispose();
  },


  // `fetchAnswer` will call the `loadAnswer` action creator. If either
  // `query.numrecs` or `query.offset` is not set, we will replace the current
  // URL by setting the query params to some default values. Otherwise, we will
  // call the `loadAnswer` action creator based on the `params` and `query`
  // objects.
  fetchAnswer(props) {
    let { preferenceStore } = this.context.stores;
    let { answerActions } = this.context.actions;

    // props.params and props.query are passed to this component by the Router.
    let params = props.params;
    let query = props.query;

    // Get pagination info from `query`
    let pagination = {
      numRecords: query.numrecs || 1000,
      offset: query.offset || 0
    };

    // XXX Could come from query param: sorting={attributeName}__{direction},...
    let sorting = preferenceStore.value.preferences[this.sortingPreferenceKey];
    if (sorting === undefined) {
      sorting = [{ attributeName: 'primary_key', direction: 'ASC' }];
    }

    // Combine `pagination` and `sorting` into a single object:
    //
    //     let displayInfo = {
    //       pagination: pagination,
    //       sorting: sorting
    //     };
    //
    let displayInfo = {
      pagination,
      sorting,
      visibleAttributes: this.state && this.state.displayInfo.visibleAttributes
    };

    // TODO Add params to loadAnswer call
    let answerParams = wrap(query.param).map(p => {
      let parts = p.split('__');
      return { name: parts[0], value: parts[1] };
    });

    let opts = {
      displayInfo,
      params: answerParams
    };

    // Call the AnswerCreator to fetch the Answer resource
    let filterOpts = {
      questionName: params.questionName,
      terms: query.q,
      attributes: maybeSplit(query.attrs, ','),
      tables: maybeSplit(query.tables, ',')
    };

    answerActions.updateFilter(filterOpts);
    answerActions.loadAnswer(params.questionName, opts);
  },


  // This is a collection of event handlers that will be passed to the Answer
  // component (which will, in turn, pass these to the AnswerTable component.
  // In our render() method, we will bind these methods to the component
  // instance so that we can use `this` as expected. Normally, React will do
  // this for us, but since we are nesting methods within a property, we have
  // to do this ourselves. This is all really simply for brevity in code when
  // we pass these handers to the Answer component. It will also make
  // refactoring a little easier in the future.
  answerEvents: {

    // Update the sorting of the Answer resource. In this handler, we trigger a
    // call to udpate the sorting by updating the URL via `this.replaceWith`.
    // This will cause `this.componentWillReceiveProps` to be called. See the
    // comment below for an alternative way calling `loadAnswer` directly. Yet
    // another way would be to have a `sortAnswer` action creator.
    onSort(attribute, direction) {

      // Update the query object with the new values.
      // See https://lodash.com/docs#assign
      // let query = Object.assign({}, this.props.query, {
      //   sortBy: attribute.name,
      //   sortDir: direction
      // });

      // This method is provided by the `Router.Navigation` mixin. It will
      // update the URL, which will trigger a new Route event, which will cause
      // this `this.componentWillReceiveProps` to be called, which will cause
      // this component to call `this.fetchAnswer()` with the sorting
      // configuration.
      // this.context.router.replaceWith('answer', this.props.params, query);

      // This is an alternative way, which is to call loadAnswer.
      // The appeal of the above is that if the user clicks the browser refresh
      // button, they won't lose their sorting. We can also acheive that by
      // saving the display info to localStorage and reloading it when the page
      // is reloaded.
      //
      let attributeName = attribute.name;
      let { displayInfo, question } = this.state;
      let newSort = { attributeName, direction };
      // Create a new array by removing existing sort def for attribute
      // and adding the new sort def to the beginning of the array, only
      // retaining the last three defs.
      let sorting = displayInfo.sorting.
        filter(spec => spec.attributeName !== attributeName).
        slice(0, 2);

      sorting = [newSort].concat(sorting);

      displayInfo = update(displayInfo, {
        sorting: { $set: sorting }
      });

      this.context.actions.answerActions.loadAnswer(question.name, { displayInfo });

      this.context.actions.preferenceActions.setPreference(this.sortingPreferenceKey, sorting);
    },

    // Call the `moveColumn` action creator. This will cause the state of
    // the answer store to be updated. That will cause the state of this
    // component to be updated, which will cause the `render` method to be
    // called.
    onMoveColumn(columnName, newPosition) {
      this.context.actions.answerActions.moveColumn(columnName, newPosition);
    },

    // Call the `changeAttributes` action creator. This will cause the state of
    // the answer store to be updated. That will cause the state of this
    // component to be updated, which will cause the `render` method to be
    // called.
    onChangeColumns(attributes) {
      this.context.actions.answerActions.changeAttributes(attributes);
    },

    onToggleFormat() {
      let path = 'answer';
      let params = this.props.params;
      let query = this.props.query;

      // update query with format and position
      query.format = !query.format || query.format === 'table'
        ? 'list' : 'table';

      // Method provided by Router.Navigation mixin
      this.context.router.transitionTo(path, params, query);
    },

    onFilter(terms, attributes, tables) {
      if (!terms) {
        terms = attributes = tables = undefined;
      }

      let query = update(this.props.query, {
        $merge: {
          q: terms,
          attrs: maybeJoin(attributes, ',') || undefined,
          tables: maybeJoin(tables, ',') || undefined
        }
      });

      this.context.router.transitionTo('answer', this.props.params, query);
    }

  },

  // `render` is called when React.renderComponent is first invoked, and anytime
  // `props` or `state` changes. This latter will happen when any stores are
  // changed.
  //
  // TODO - Explain what's happening here in more detail.
  render() {

    // If state is null, we can assume the stores have no been populated yet.
    // We can remove this logic by moving the store subscription to a generic
    // parent component. Doing so will allow the state of the stores to be
    // passed as props to this component. This null check would happen in the
    // parent component simplifying the logic in this component.
    //
    // We return null here to indicate to React that there is nothing to render.
    if (this.state == null) return null;

    // use "destructuring" syntax to assign this.props.params.questionName to questionName
    let {
      isLoading,
      answer,
      displayInfo,
      filterTerm,
      filterAttributes,
      filterTables,
      filteredRecords,
      questions,
      recordClasses
    } = this.state;

    let { params } = this.props;

    let question = questions.find(q => q.name === params.questionName);
    let recordClass = recordClasses.find(r => r.fullName === question.class);

    // Bind methods of `this.answerEvents` to `this`. When they are called by
    // child elements, any reference to `this` in the methods will refer to
    // this component.
    let answerEvents = Object.keys(this.answerEvents)
      .reduce((events, key) => {
        events[key] = this.answerEvents[key].bind(this);
        return events;
      }, {});

    // Valid formats are 'table' and 'list'
    // TODO validation
    let format = this.props.query.format || 'table';

    // `{...this.state}` is JSX short-hand syntax to pass each key-value pair of
    // this.state as a property to the component. It intentionally resembles
    // the JavaScript spread operator.
    //
    // See http://facebook.github.io/react/docs/transferring-props.html#transferring-with-...-in-jsx
    // and https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Spread_operator
    //
    // to understand the embedded XML, see: https://facebook.github.io/react/docs/jsx-in-depth.html
    if (answer && question && recordClass) {
      if (filterAttributes.length === 0 && filterTables.length === 0) {
        filterAttributes = recordClass.attributes.map(a => a.name);
        filterTables = recordClass.tables.map(t => t.name);
      }
      return (
        <Doc title={`${question.displayName}`}>
          {isLoading ? <Loading/> : null}
          <Answer
            answer={answer}
            question={question}
            recordClass={recordClass}
            displayInfo={displayInfo}
            filterTerm={filterTerm}
            filteredRecords={filteredRecords}
            filterAttributes={filterAttributes}
            filterTables={filterTables}
            format={format}
            answerEvents={answerEvents}
          />
        </Doc>
      );
    }

    return <Loading/>;
  }

});

// Export the React Component class we just created.
export default wrappable(AnswerController);
