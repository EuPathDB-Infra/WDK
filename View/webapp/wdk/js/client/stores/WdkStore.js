import { ReduceStore } from 'flux/utils';
import { StaticDataProps } from '../utils/StaticDataUtils';
import { staticDataConfigMap } from '../actioncreators/StaticDataActionCreator';
import { actionTypes as userActionTypes } from '../actioncreators/UserActionCreator';

// create a map to static data item configs, but with actionTypes as keys instead of prop names
let actionMap = Object.keys(staticDataConfigMap).reduce((actionMap, key) =>
    Object.assign(actionMap, { [staticDataConfigMap[key].actionType]: staticDataConfigMap[key] }), {});

export default class WdkStore extends ReduceStore {

  constructor(dispatcher, storeContainer) {
    super(dispatcher);
    this.getStoreContainer = () => {
      console.warn("Deprecated; we no longer want to share data between stores.");
      return storeContainer;
    };
  }

  /**
   * Returns an Array<string> representing static props this store needs.
   * Override to automatically load static data items.  Strings passed should be
   * constants in StaticDataActionCreator.StaticData and are ignored otherwise.
   */
  getRequiredStaticDataProps() {
    return [];
  }

  /**
   * Returns true if values exist in the passed state for each property
   * requested in getRequiredStaticDataProps(), else false.
   */
  isAllRequiredStaticDataLoaded(state) {
    return this.getRequiredStaticDataProps().every(prop => (state[prop] != null));
  }

  /**
   * Handles requested static data item loads and passes remaining actions to
   * handleAction(), which will usually be overridden by the subclass
   */
  reduce(state, action) {
    let { type, payload } = action;
    let requiredProps = this.getRequiredStaticDataProps();
    if (type in actionMap && requiredProps.includes(actionMap[type].elementName)) {
      // this action corresponds to a static data item that this store needs
      return this.handleStaticDataItemAction(state, actionMap[type].elementName, payload);
    }
    // special cases for updates to user data (static but not const)
    else if (type === userActionTypes.USER_UPDATE && requiredProps.includes(StaticDataProps.USER)) {
      // treat new user object as if it has just been loaded
      return this.handleStaticDataItemAction(state, StaticDataProps.USER, payload);
    }
    // special cases for updates to preference data (static but not const)
    else if (type === userActionTypes.USER_PREFERENCE_UPDATE && requiredProps.includes(StaticDataProps.PREFERENCES)) {
      // incorporate new preference values into existing preference object
      let newPrefs = Object.assign({}, state[StaticDataProps.PREFERENCES], payload);
      // treat preference object as if it has just been loaded (with new values present)
      return this.handleStaticDataItemAction(state, StaticDataProps.PREFERENCES, newPrefs);
    }
    else {
      let newState = this.handleAction(state, action);
      if (newState == null) {
        console.warn("Null or undefined state returned from handleAction method of " + this.constructor.name);
      }
      return newState;
    }
  }

  /**
   * Default handling of each static data item load.  Will only be called with
   * required static data items.
   */
  handleStaticDataItemAction(state, itemName, payload) {
    return Object.assign({}, state, { [itemName]: payload[itemName] });
  }

  /**
   * Does nothing by default for other actions; subclasses will probably override
   */
  handleAction(state, action) {
    return state;
  }
}
