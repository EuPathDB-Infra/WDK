import { wrappable } from '../utils/componentUtils';
import { getNodeChildren } from '../utils/OntologyUtils';
import { getNodeId, nodeSearchPredicate, BasicNodeComponent } from '../utils/CategoryUtils';
import CheckboxTree from './CheckboxTree';

let CategoriesCheckboxTree = props => {

  let { title, searchBoxPlaceholder, tree, selectedLeaves, expandedBranches,
    isMultiPick, searchText, onChange, onUiChange, onSearchTextChange } = props;

  if (isMultiPick === undefined) isMultiPick = true;

  if (tree.children.length == 0) {
    return ( <noscript/> );
  }

  let treeProps = {

    // set hard-coded values for searchable, selectable, expandable tree
    isSearchable: true, isSelectable: true,

    // set values from category utils since we know tree is a category tree
    getNodeId, getNodeChildren, searchPredicate: nodeSearchPredicate, nodeComponent: BasicNodeComponent,

    // set current data in the tree
    tree, isMultiPick, selectedList: selectedLeaves, expandedList: expandedBranches, searchBoxPlaceholder, searchText,

    // set event handlers
    onSelectionChange: onChange, onExpansionChange: onUiChange, onSearchTextChange: onSearchTextChange
  };

  return (
    <div>
      <h3>{title}</h3>
      <div style={{padding: '0 2em'}}>
        <CheckboxTree {...treeProps} />
      </div>
    </div>
  );
};

export default wrappable(CategoriesCheckboxTree);
