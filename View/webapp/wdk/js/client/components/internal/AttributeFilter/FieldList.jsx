import React from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import { memoize, uniq } from 'lodash';
import { isFilterField, isRange, isMulti, getTree } from "../../../utils/FilterServiceUtils";
import { Seq } from '../../../utils/IterableUtils';
import CheckboxTree from '../../CheckboxTree';
import Icon from '../../IconAlt';
import Tooltip from '../../Tooltip';

/**
 * Tree of Fields, used to set the active field.
 */
export default class FieldList extends React.PureComponent {

  constructor(props) {
    super(props);
    this.handleCheckboxTreeRef = this.handleCheckboxTreeRef.bind(this);
    this.getNodeId = this.getNodeId.bind(this);
    this.getNodeChildren = this.getNodeChildren.bind(this);
    this.handleExpansionChange = this.handleExpansionChange.bind(this);
    this.handleSearchTermChange = this.handleSearchTermChange.bind(this);
    this.renderNode = this.renderNode.bind(this);
    this.searchPredicate = this.searchPredicate.bind(this);
    this.getFieldSearchString = memoize(this.getFieldSearchString);

    this.state = {
      searchTerm: '',

      // expand branch containing selected field
      expandedNodes: this._getPathToField(this.props.selectedField)
    };
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.selectedField == null || this.props.selectedField === nextProps.selectedField) return;

    if (
      nextProps.selectedField.parent != null &&
      !this.state.expandedNodes.includes(nextProps.selectedField.parent)
    ) {
      this.setState({
        expandedNodes: uniq(this.state.expandedNodes.concat(
          this._getPathToField(nextProps.selectedField)))
      });
    }
  }

  componentDidUpdate(prevProps) {
    if (prevProps.selectedField !== this.props.selectedField) {
      this._scrollSelectedFieldIntoView();
    }
  }

  handleCheckboxTreeRef(component) {
    this.treeDomNode = ReactDOM.findDOMNode(component);
  }

  handleExpansionChange(expandedNodes) {
    this.setState({ expandedNodes });
  }

  handleFieldSelect(node) {
    this.props.onFieldSelect(node.field.term);
    const expandedNodes = Seq.from(this.state.expandedNodes)
      .concat(this._getPathToField(node.field))
      .concat(node.children.length > 0 ? Seq.of(node.field.term) : Seq.empty())
      .uniq()
      .toArray();
    this.setState({ expandedNodes });
  }

  handleSearchTermChange(searchTerm) {
    // update search term, then if it is empty, make sure selected field is visible
    this.setState({searchTerm}, () => {
      if (searchTerm == '') this._scrollSelectedFieldIntoView();
    });
  }

  _scrollSelectedFieldIntoView() {
    if (this.selectedFieldDOMNode != null) {
      let scrollList = this.treeDomNode.querySelector('.wdk-CheckboxTreeList');
      if (
        scrollList != null &&
        // below the bottom
        ((scrollList.scrollTop > this.selectedFieldDOMNode.offsetTop) ||
        (scrollList.clientHeight + scrollList.scrollTop) <= (this.selectedFieldDOMNode.offsetTop + this.selectedFieldDOMNode.clientHeight))
        // above the top
      ) {
        scrollList.scrollTop = this.selectedFieldDOMNode.offsetTop - (scrollList.clientHeight / 2);
      }
    }
  }

  getNodeId(node) {
    return node.field.term;
  }

  getNodeChildren(node) {
    return isMulti(node.field) ? [] : node.children;
  }

  renderNode({node}) {
    let isActive = this.props.selectedField === node.field;
    return (
      <Tooltip content={node.field.description} hideDelay={0}>
        {isFilterField(node.field)
        ? (
          <a
            className={'wdk-AttributeFilterFieldItem' +
              (isActive ? ' wdk-AttributeFilterFieldItem__active' : '')}
            href={'#' + node.field.term}
            onClick={e => {
              e.preventDefault();
              e.stopPropagation();
              this.handleFieldSelect(node);
              this.selectedFieldDOMNode = e.target;
            }}>
            <Icon fa={isRange(node.field) ? 'bar-chart-o' : 'list'}/> {node.field.display}
          </a>
        ) : (
          <div className="wdk-Link wdk-AttributeFilterFieldParent">{node.field.display}</div>
        )}
      </Tooltip>
    );
  }

  getFieldSearchString(field) {
    return [
      field.display,
      field.description ? field.description : '',
      field.values ? field.values : ''
    ]
    .join(' ')
    .toLowerCase();
  }

  searchPredicate(node, searchTerms) {
    return searchTerms.every(searchTerm =>
      this.getFieldSearchString(node.field).includes(searchTerm.toLowerCase())
    )
  }

  _getPathToField(field, path = []) {
    if (field == null || field.parent == null) return path;
    return this._getPathToField(this.props.fields.get(field.parent),
      path.concat(field.parent))
  }

  render() {
    var { autoFocus, fields } = this.props;

    return (
      <div className="field-list">
        <CheckboxTree
          ref={this.handleCheckboxTreeRef}
          autoFocusSearchBox={autoFocus}
          tree={getTree(fields.values())}
          expandedList={this.state.expandedNodes}
          getNodeId={this.getNodeId}
          getNodeChildren={this.getNodeChildren}
          onExpansionChange={this.handleExpansionChange}
          isSelectable={false}
          nodeComponent={this.renderNode}
          isSearchable={true}
          searchBoxPlaceholder="Find a quality..."
          searchBoxHelp="Find a quality by searching names and descriptions"
          searchTerm={this.state.searchTerm}
          onSearchTermChange={this.handleSearchTermChange}
          searchPredicate={this.searchPredicate}
        />
      </div>
    );
  }
}

FieldList.propTypes = {
  autoFocus: PropTypes.bool,
  fields: PropTypes.instanceOf(Map).isRequired,
  onFieldSelect: PropTypes.func.isRequired,
  selectedField: PropTypes.object
};
