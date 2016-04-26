import { Component, PropTypes } from 'react';
import pick from 'lodash/object/pick';

import CheckboxTreeNode from './CheckboxTreeNode';
import RealTimeSearchBox from './RealTimeSearchBox';

import { addOrRemove, propsDiffer } from '../utils/componentUtils';
import { isLeaf, getLeaves, getBranches, mapStructure } from '../utils/TreeUtils';

const NODE_STATE_PROPERTY = '__expandableTreeState';
const NODE_CHILDREN_PROPERTY = '__expandableTreeChildren';

/**
 * Renders tree links to select, clear, expand, collapse all nodes, or reset to current or default
 */
let TreeLinks = props => {
  let {
    showSelectionLinks, showExpansionLinks, showCurrentLink, showDefaultLink,
    selectAll, selectNone, expandAll, expandNone, selectCurrentList, selectDefaultList
  } = props;
  return (
    <div className="wdk-CheckboxTree-links">
      { showSelectionLinks &&
        <span>
          <a href="#" onClick={selectAll}>select all</a> |
          <a href="#" onClick={selectNone}> clear all</a>
          <br/>
        </span> }

      { showExpansionLinks &&
        <span>
          <a href="#" onClick={expandAll}> expand all</a> |
          <a href="#" onClick={expandNone}> collapse all</a>
          <br/>
        </span> }

      { showSelectionLinks &&
        <span>
          { showCurrentLink && <a href="#" onClick={selectCurrentList}>reset to current</a> }
          { showCurrentLink && showDefaultLink ? " | " : "" }
          { showDefaultLink && <a href="#" onClick={selectDefaultList}>reset to default</a> }
          { (showCurrentLink || showDefaultLink) && <br/> }
        </span> }
    </div>
  );
};

/**
 * Creates a function that will handle a click of one of the tree links above
 */
let createLinkHandler = (treeObj, idListFetcher, changeHandlerProp) => {
  return (event => {

    // prevent update to URL
    event.preventDefault();

    // call instance's change handler with the appropriate ids
    let idList = idListFetcher();
    if (idList !== undefined && idList !== null) {
      treeObj.props[changeHandlerProp](idList);
    }

  });
};

/**
 * Creates a function that will handle expansion-related tree link clicks
 */
let createExpander = (treeObj, listFetcher) =>
  createLinkHandler(treeObj, listFetcher, 'onExpansionChange');

/**
 * Creates a function that will handle selection-related tree link clicks
 */
let createSelector = (treeObj, listFetcher) =>
  createLinkHandler(treeObj, listFetcher, 'onSelectionChange');

/**
 * Creates appropriate initial state values for a node in the stateful tree
 */
let getInitialNodeState = (node, getNodeChildren) => (
  Object.assign({}, {
    // these state properties apply to all nodes
    isSelected: false, isVisible: true
  }, isLeaf(node, getNodeChildren) ? {} : {
    // these state properties only apply to branch nodes (not leaves)
    isExpanded: false, isIndeterminate: false
  })
);

/**
 * Creates a copy of the input tree, populating each node with initial state.
 * Note this initial state is generic and not dependent on props.  The first
 * call to applyPropsToStatefulTree() applies props to an existing stateful tree.
 */
let createStatefulTree = (root, getNodeChildren) => {
  let mapFunction = (node, mappedChildren) =>
      Object.assign({}, node, { [NODE_CHILDREN_PROPERTY]: mappedChildren,
        [NODE_STATE_PROPERTY]: getInitialNodeState(node, getNodeChildren) });
  return mapStructure(mapFunction, getNodeChildren, root);
};

/**
 * Applies a set of expandable tree props to an existing stateful tree (a copy
 * of the input tree with additional state applied).  The resulting tree is a
 * copy of the input stateful tree, with any unchanged nodes staying the same
 * (i.e. same object) so node rendering components can use referential equality
 * to decide whether to re-render.  Any parent of a modified node is replaced
 * with a new node to ensure any modified branches are re-rendered up to the
 * root node.
 * 
 * In addition to the replaced tree, the returned object contains the list of
 * expanded nodes.  If an expanded node list is sent in as a prop, it is used,
 * but if the prop is empty or null, the expanded list is generated by the
 * checkbox tree according to the following rules:
 * 
 * - if all descendent leaves are selected, the node is collapsed
 * - if no descendent leaves are selected, the node is collapsed
 * - if some but not all descendent leaves are selected, the node is expanded
 */
let applyPropsToStatefulTree = (root, props, isLeafVisible, stateExpandedList) => {

  let { getNodeId, getNodeChildren, isSelectable, isMultiPick, selectedList } = props;
  let propsExpandedList = props.expandedList;

  // if single-pick then trim selected list so at most 1 item present
  if (!isMultiPick && selectedList.length > 1) {
    console.warn("CheckboxTree: isMultiPick = false, but more than one item selected.  Ignoring all but first item.");
    selectedList = [ selectedList[0] ];
  }

  // if expanded list is null, then use default rules to determine expansion rather than explicit list
  let expandedList = propsExpandedList != null ? propsExpandedList : stateExpandedList;
  let expansionListProvided = (expandedList != null);
  let generatedExpandedList = new Set();

  // convert arrays to sets for search efficiency
  selectedList = new Set(selectedList);
  expandedList = new Set(expandedList);
  
  let mapFunction = (node, mappedChildren) => {

    let nodeId = getNodeId(node);
    let { isSelected, isVisible, isExpanded, isIndeterminate } = getNodeState(node);
    let newState = Object.assign({}, getNodeState(node));
    let modifyThisNode = false;

    if (isLeaf(node, getNodeChildren)) {
      // only leaves can change via direct selectedness and direct visibility
      let newIsSelected = (isSelectable && selectedList.has(nodeId));
      let newIsVisible = isLeafVisible(nodeId);
      if (newIsSelected !== isSelected || newIsVisible != isVisible) {
        modifyThisNode = true;
        newState = Object.assign(newState, {
          isSelected: newIsSelected,
          isVisible: newIsVisible
        });
      }
    }
    else {
      // branches can change in all ways; first inspect children to gather information
      let selectedChildFound = false;
      let unselectedChildFound = false;
      let indeterminateChildFound = false;
      let visibleChildFound = false;

      let oldChildren = getStatefulChildren(node);
      for (let i = 0; i < oldChildren.length; i++) {
        let newChild = mappedChildren[i];
        if (newChild !== oldChildren[i]) {
          // reference equality check failed; a child has been modified, so must modify this node
          modifyThisNode = true;
        }
        let newChildState = getNodeState(newChild);
        if (newChildState.isSelected)
          selectedChildFound = true;
        else
          unselectedChildFound = true;
        if (newChildState.isIndeterminate)
          indeterminateChildFound = true;
        if (newChildState.isVisible)
          visibleChildFound = true;
      }

      // determine new state and compare with old to determine if this node should be modified
      let newIsSelected = (!indeterminateChildFound && !unselectedChildFound);
      let newIsIndeterminate = !newIsSelected && (indeterminateChildFound || selectedChildFound);
      let newIsVisible = visibleChildFound;
      let newIsExpanded = (isActiveSearch(props) && newIsVisible) ||
          (expansionListProvided ? expandedList.has(nodeId) :
              (indeterminateChildFound || (selectedChildFound && unselectedChildFound)));

      if (!expansionListProvided && newIsExpanded) {
        generatedExpandedList.add(nodeId);
      }

      if (modifyThisNode ||
          newIsSelected !== isSelected ||
          newIsIndeterminate !== isIndeterminate ||
          newIsExpanded !== isExpanded ||
          newIsVisible !== isVisible) {
        modifyThisNode = true;
        newState = Object.assign(newState, {
          isSelected: newIsSelected,
          isVisible: newIsVisible,
          isIndeterminate: newIsIndeterminate,
          isExpanded: newIsExpanded
        });
      }
    }

    // return the existing node if no changes present in this or children; otherwise create new
    return (!modifyThisNode ? node : Object.assign({}, node,
        { [NODE_CHILDREN_PROPERTY]: mappedChildren, [NODE_STATE_PROPERTY]: newState }));
  }

  // generate the new stateful tree, and expanded list (if necessary)
  let newStatefulTree = mapStructure(mapFunction, getStatefulChildren, root);
  return {
    // convert whichever Set we want back to an array
    expandedList: [...(expansionListProvided ? expandedList : generatedExpandedList)],
    statefulTree: newStatefulTree
  };
};

/**
 * Returns true if a search is being actively performed (i.e. if this tree is
 * searchable, and search text is non-empty).
 */
let isActiveSearch = ({ isSearchable, searchTerm }) =>
    isSearchable && searchTerm.length > 0;

/**
 * Returns a function that takes a leaf node ID and returns true if leaf node
 * should be visible.  If no search is being performed, all leaves are visible,
 * unless one of their ancestors is collapsed.  In that case visibility of the
 * leaf container is controlled by a parent, so the function returned here will
 * still return true.
 * 
 * If a search is being actively performed, matching nodes, their children, and
 * their ancestors, will be visible (expansion is locked and all branches are
 * expanded).  The function returned by createIsLeafVisible does not care about
 * branches, but tells absolutely if a leaf should be visible (i.e. if the leaf
 * matches the search or if any ancestor matches the search).
 */
let createIsLeafVisible =  props => {
  let { tree, isSearchable, searchTerm, searchPredicate, getNodeId, getNodeChildren } = props;
  // if not searching, then all nodes are visible
  if (!isActiveSearch({ isSearchable, searchTerm })) {
    return nodeId => true;
  }
  // otherwise must construct array of visible leaves
  let visibleLeaves = new Set();
  let addVisibleLeaves = (node, parentMatches) => {
    // if parent matches, automatically match (always show children of matching parents)
    let nodeMatches = (parentMatches || searchPredicate(node, searchTerm));
    if (isLeaf(node, getNodeChildren)) {
      if (nodeMatches) {
        visibleLeaves.add(getNodeId(node));
      }
    }
    else {
      getNodeChildren(node).forEach(child => {
        addVisibleLeaves(child, nodeMatches);
      });
    }
  }
  addVisibleLeaves(tree, false);
  return nodeId => visibleLeaves.has(nodeId);
};

/**
 * Returns the stateful children of a node in a stateful tree.  Should be used
 * in lieu of the getNodeChildren prop when rendering the tree.
 */
let getStatefulChildren = node => node[NODE_CHILDREN_PROPERTY];

/**
 * Returns the state of a node in the stateful tree
 */
let getNodeState = node => node[NODE_STATE_PROPERTY];

/**
 * Expandable tree component
 */
export default class CheckboxTree extends Component {

  /**
   * Hards binds all the user interaction methods to this object.
   * @param props - component properties
   */
  constructor(props) {
    super(props);

    // destructure props needed in this function
    let { tree, getNodeId, getNodeChildren } = props;

    // create stateful isLeafVisible function based on props; this will be stored in state
    let isLeafVisible = createIsLeafVisible(props);

    // initialize stateful tree; this immutable tree structure will be replaced with each state change
    this.state = {
      isLeafVisible: isLeafVisible,
      generated: applyPropsToStatefulTree(createStatefulTree(tree, getNodeChildren), props, isLeafVisible, null)
    };

    // define event handlers related to expansion
    this.expandAll = createExpander(this, () => getBranches(this.props.tree, getNodeChildren).map(node => getNodeId(node)));
    this.expandNone = createExpander(this, () => []);
    this.toggleExpansion = this.toggleExpansion.bind(this);

    // define event handlers related to selection
    this.selectAll = createSelector(this, () => getLeaves(this.props.tree, getNodeChildren).map(node => getNodeId(node)));
    this.selectNone = createSelector(this, () => []);
    this.selectCurrentList = createSelector(this, () => this.props.currentList);
    this.selectDefaultList = createSelector(this, () => this.props.defaultList);
    this.toggleSelection = this.toggleSelection.bind(this);
  }

  /**
   * When new props are passed, the checkbox tree must apply them to the existing
   * stateful tree to determine which nodes have changed due to changing props.
   * 
   * Also if search-related props have changed, the isLeafVisible function must
   * be regenerated so the currently-matching nodes are made visible.
   */
  componentWillReceiveProps(nextProps) {
    let { tree, getNodeChildren, isSearchMode, searchTerm, searchPredicate } = nextProps;

    // create new isLeafVisible if relevant props have changed
    let recreateIsLeafVisible = propsDiffer(this.props, nextProps,
        ['isSearchable', 'searchTerm', 'searchPredicate']);
    let isLeafVisible = (recreateIsLeafVisible ? createIsLeafVisible(nextProps) : this.state.isLeafVisible);

    // if certain props have changed, then recreate stateful tree from scratch;
    //     otherwise apply props to the existing tree to improve performance
    let recreateGeneratedState = propsDiffer(this.props, nextProps,
        [ 'tree', 'name', 'getNodeId', 'getNodeChildren', 'nodeComponent' ]);
    this.setState({
      isLeafVisible: isLeafVisible,
      generated: applyPropsToStatefulTree((recreateGeneratedState ?
          createStatefulTree(tree, getNodeChildren) :
          this.state.generated.statefulTree),
        nextProps, isLeafVisible, this.state.generated.expandedList)
    });
  }

  /**
   * Toggle expansion of the given node.  If node is a leaf, does nothing.
   */
  toggleExpansion(node) {
    let { getNodeId, getNodeChildren, onExpansionChange } = this.props;
    if (!isActiveSearch(this.props) && !isLeaf(node, getNodeChildren)) {
      this.props.onExpansionChange(addOrRemove(this.state.generated.expandedList, getNodeId(node)));
    }
  }

  /**
   * Toggle selection of the given node.
   * If toggled checkbox is a selected leaf - add the leaf to the select list to be returned
   * If toggled checkbox is an unselected leaf - remove the leaf from the select list to be returned
   * If toggled checkbox is a selected non-leaf - identify the node's leaves and add them to the select list to be returned
   * If toggled checkbox is an unselected non-leaf - identify the node's leaves and remove them from the select list to be returned
   */
  toggleSelection(node, selected) {
    let { isSelectable, getNodeId, getNodeChildren, isMultiPick, selectedList, onSelectionChange } = this.props;
    if (!isSelectable) {
      return;
    }
    if (isLeaf(node, getNodeChildren)) {
      if (isMultiPick) {
        onSelectionChange(addOrRemove(selectedList, getNodeId(node)));
      }
      else {
        // radio button will only fire if changing from unselected -> selected;
        //   if single-pick, any event means only the clicked node is the new list
        onSelectionChange([ getNodeId(node) ]);
      }
    }
    else {
      let newSelectedList = (selectedList ? selectedList.slice() : []);
      let leafNodes = getLeaves(node, getNodeChildren);
      leafNodes.forEach(leafNode => {
        let leafId = getNodeId(leafNode);
        let index = newSelectedList.indexOf(leafId);
        if (selected && index === -1) {
          newSelectedList.push(leafId);
        }
        else if (!selected && index > -1) {
          newSelectedList.splice(index, 1);
        }
      });
      onSelectionChange(newSelectedList);
    }
  }

  /**
   * Renders the expandable tree and related components
   */
  render() {
    let {
      name, showRoot, getNodeId, nodeComponent, isSelectable, isMultiPick,
      isSearchable, currentList, defaultList, showSearchBox, searchTerm,
      searchBoxPlaceholder, searchBoxHelp, onSearchTermChange
    } = this.props;
    let treeLinkHandlers =
      pick(this, [ 'selectAll', 'selectNone', 'expandAll', 'expandNone',
                   'selectCurrentList', 'selectDefaultList' ]);
    let topLevelNodes = (showRoot ? [ this.state.generated.statefulTree ] :
      getStatefulChildren(this.state.generated.statefulTree));
    let treeLinks = (
      <TreeLinks {...treeLinkHandlers} showSelectionLinks={isSelectable && isMultiPick}
        showCurrentLink={currentList != null} showDefaultLink={defaultList != null}
        showExpansionLinks={!isActiveSearch({ isSearchable, searchTerm })} />
    );
    let myNodeComponent = (nodeComponent != null ? nodeComponent :
      (props => <span>{getNodeId(props.node)}</span>));
    return (
      <div className="wdk-CheckboxTree">
        {treeLinks}
        {!isSearchable || !showSearchBox ? "" : (
          <RealTimeSearchBox
            initialSearchTerm={searchTerm}
            onSearchTermChange={onSearchTermChange}
            placeholderText={searchBoxPlaceholder}
            helpText={searchBoxHelp} />
        )}
        <ul className="fa-ul wdk-CheckboxTree-list">
          {topLevelNodes.map(node =>
            <CheckboxTreeNode
              key={"node_" + getNodeId(node)}
              name={name}
              node={node}
              getNodeState={getNodeState}
              isSelectable={isSelectable}
              isMultiPick={isMultiPick}
              isActiveSearch={isActiveSearch(this.props)}
              toggleSelection={this.toggleSelection}
              toggleExpansion={this.toggleExpansion}
              getNodeId={getNodeId}
              getNodeChildren={getStatefulChildren}
              nodeComponent={myNodeComponent} />
          )}
        </ul>
        {treeLinks}
      </div>
    );
  }
}

CheckboxTree.defaultProps = {
  showRoot: false,
  expandedList: null,
  isSelectable: false,
  selectedList: [],
  isMultiPick: true,
  onSelectionChange: () => {},
  isSearchable: false,
  showSearchBox: true,
  searchBoxPlaceholder: "Search...",
  searchBoxHelp: '',
  searchTerm: '',
  onSearchTermChange: () => {},
  searchPredicate: () => true
}

CheckboxTree.propTypes = {

  //%%%%%%%%%%% Basic expandable tree props %%%%%%%%%%%

  /** Node representing root of the data to be rendered as an expandable tree */
  tree: PropTypes.object.isRequired,

  /** Takes a node, returns unique ID for this node; ID is used as input value of the nodes checkbox if using selectability */
  getNodeId: PropTypes.func.isRequired,

  /** Takes a node, Called during rendering to provide the children for the current node */
  getNodeChildren:  PropTypes.func.isRequired,

  /** Called when the set of expanded (branch) nodes changes.  The function will be called with the array of the expanded node ids.  If omitted, no handler is called. */
  onExpansionChange: PropTypes.func.isRequired,

  /** Whether to show the root node or start with the array of children; optional, defaults to false */
  showRoot: PropTypes.bool,

  /** Called during rendering to create the react element holding the display name and tooltip for the current node, defaults to <span>{this.props.getNodeId(node)</span> */
  nodeComponent: PropTypes.func,

  /** List of expanded nodes as represented by their ids, default to null; if null, expandedList will be generated by the expandable tree. */
  expandedList: PropTypes.array,

  //%%%%%%%%%%% Properties associated with selectability %%%%%%%%%%%

  /** If true, checkboxes and ‘select…’ links are shown and the following parameters are honored, else no checkboxes or ‘select…’ links are shown and props below are ignored; default to false */
  isSelectable: PropTypes.bool,

  /** List of selected nodes as represented by their ids, defaults to [ ] */
  selectedList: PropTypes.array,

  /** Tells whether more than one selection is allowed; defaults to true.  If false, only the first item in selectedList is selected, and radio boxes are rendered. */
  isMultiPick: PropTypes.bool,

  /** Value to use for the name of the checkboxes in the tree */
  name: PropTypes.string,

  /** Takes array of ids, thus encapsulates:
       selectAll, clearAll, selectDefault, selectCurrent (i.e. reset) */
  onSelectionChange: PropTypes.func,
 
 /** List of “current” ids, if omitted (undefined or null), then don’t display link */
  currentList: PropTypes.array,

  /** List of default ids, if omitted (undefined or null), then don’t display link */
  defaultList: PropTypes.array,

  //%%%%%%%%%%% Properties associated with search %%%%%%%%%%%

  /** Indicates whether this is a searchable CBT.  If so, then show boxes and respect the optional parameters below, also turn off expansion; default to false */
  isSearchable: PropTypes.bool,

  /** Whether to show search box; defaults to true (but only if isSearchable is true).  Useful if searching is controlled elsewhere */
  showSearchBox: PropTypes.bool,

  /** PlaceHolder text; shown in grey if searchTerm is empty */
  searchBoxPlaceholder: PropTypes.string,

  /** Search box help text: if present, a help icon will appear; mouseover the icon and a tooltip will appear with this text */
  searchBoxHelp: PropTypes.string,

  /** Current search term; if non-empty, expandability is disabled */
  searchTerm: PropTypes.string,

  /** Takes single arg: the new search text.  Called when user types into the search box */
  onSearchTermChange: PropTypes.func,

  /** Takes (node, searchTerm) and returns boolean. Tells whether a node matches search criteria and should be shown */
  searchPredicate: PropTypes.func
};
