import './exposeModules';

import mapValues from 'lodash/object/mapValues';
import values from 'lodash/object/values';
import pick from 'lodash/object/pick';

import Dispatcher from './dispatcher/Dispatcher';
import WdkService from './utils/WdkService';
import * as Router from './router';
import * as ActionCreators from './actioncreators';
import * as Components from './components';
import * as Stores from './stores';
import * as ComponentUtils from './utils/componentUtils';
import * as IterableUtils from './utils/IterableUtils';
import * as ReporterUtils from './utils/reporterUtils';
import * as TreeUtils from './utils/TreeUtils';
import * as OntologyUtils from './utils/OntologyUtils';
import * as SearchableTreeUtils from './utils/SearchableTreeUtils';
import * as FormSubmitter from './utils/FormSubmitter';
import * as WdkUtils from './utils/WdkUtils';

export { Components, Stores, ComponentUtils, ReporterUtils, FormSubmitter, WdkUtils, IterableUtils, TreeUtils, OntologyUtils, SearchableTreeUtils };

/**
 * Run the application.
 *
 * @param {string} option.rootUrl Root URL used by the router.
 * @param {string} option.endpoint Base URL for WdkService.
 * @param {HTMLElement} option.rootElement DOM node to render the applicaiton.
 * @param {Array} option.applicationRoutes Addtional routes to register with the Router.
 */
export function run({ rootUrl, endpoint, rootElement, applicationRoutes }) {
  let dispatcher = new Dispatcher;
  let wdkService = new WdkService(endpoint);
  let dispatchAction = makeDispatchAction(dispatcher, { wdkService });
  let stores = configureStores(Stores, dispatcher);
  let context = { dispatchAction, stores, };
  if (__DEV__) logActions(dispatcher, stores);
  return Router.start(rootUrl, rootElement, context, applicationRoutes);
}

/**
 * Creates a `stores` object.
 *
 * @param {Object} Stores
 * @param {Dispatcher} dispatcher
 */
function configureStores(Stores, dispatcher) {
  let stores = {};
  return Object.assign(stores,
      // filter WdkStore since it is "abstract" (does not provide implementation)
      mapValues(pick(Stores, store => store.name != 'WdkStore' ),
          Store => new Store(dispatcher, stores)));
}

/**
 * Create a dispatch function `dispatchAction` that forwards calls to
 * `dispatcher.dispatch`.
 *
 * If `action` is a function, it will be called with `dispatchAction` and
 * `services`. Calling it with `dispatchAction` allows for composability since
 * an action function can in turn call another action function. This is useful
 * for creating higher-order dispatch helpers, such as latest, once, etc.
 *
 * If `action` is an object, `dispatcher.dispatch` will be called with it.
 *
 * An `action` function should ultimately return an object to invoke a dispatch.
 *
 * @param {Dispatcher} dispatcher
 * @param {any?} services
 */
function makeDispatchAction(dispatcher, services) {
  return function dispatchAction(action) {
    try {
      if (typeof action === 'function') {
        // Call the function with dispatchAction and services
        return action(dispatchAction, services);
      }
      else if (action == null) {
        console.error("Warning: Action received is not defined or is null");
      }
      else if (action.type == null) {
        console.error("Warning: Action received does not have a `type` property", action);
      }
      return dispatcher.dispatch(action);
    }
    catch (error) {
      console.error(error);
      throw error;
    }
  }
}

/**
 * Apply Component wrappers to WDK coponents. Keys of `componentWrappers`
 * should correspond to Component names in WDK. Values of `componentWrappers`
 * are factories that return a new Component.
 *
 * @param {Object} componentWrappers
 */
export function wrapComponents(componentWrappers) {
  for (let key in componentWrappers) {
    let Component = Components[key];
    if (Component == null) {
      console.warn("Cannot wrap unknown WDK Component '" + key + "'.  Skipping...");
      continue;
    }
    if (!("wrapComponent" in Components[key])) {
      console.error(
        "Warning: WDK Component `%s` is not wrappable.  WDK version will be used.",
        key
      );
      continue;
    }
    Components[key].wrapComponent(componentWrappers[key]);
  }
}

/**
 * Apply WDK Store wrappers. Keys of `storeWrappers` should correspond to WDK
 * Store names. Values of `storeWrappers` are functions that take the current
 * Store class and return a new Store class.
 *
 * @param {Object} storeWrappers
 */
export function wrapStores(storeWrappers) {
  for (let key in storeWrappers) {
    let Store = Stores[key];
    if (Store == null) {
      console.error(
        "Warning: Cannot wrap unknown WDK Store `%s`.  Skipping...",
        key
      );
      continue;
    }
    let storeWrapper = storeWrappers[key];
    let storeWrapperType = typeof storeWrapper;
    if (storeWrapperType !== 'function') {
      console.error(
        "Expected Store wrapper for `%s` to be a `function`, but got `%s`.",
        key,
        storeWrapperType
      );
      continue;
    }
    Stores[key] = storeWrappers[key](Store);
  }
}

/**
 * Log all actions and Store state changes to the browser console.
 *
 * @param {Dispatcher} dispatcher
 * @param {Object} stores
 */
function logActions(dispatcher, stores) {
  dispatcher.register(action => {
    dispatcher.waitFor(values(stores).map(s => s.getDispatchToken()));
    console.group(action.type);
    console.info("dispatching", action);
    console.info("state", mapValues(stores, store => store.getState()));
    console.groupEnd(action.type);
  });
}
