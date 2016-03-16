import React from 'react';
import classnames from 'classnames';
import includes from 'lodash/collection/includes';
import memoize from 'lodash/function/memoize';
import RecordNavigationSectionCategories from './RecordNavigationSectionCategories';
import { postorderSeq } from '../utils/TreeUtils';
import { wrappable, PureComponent } from '../utils/componentUtils';
import {
  getId,
  getDisplayName,
  getPropertyValues,
  nodeHasProperty
} from '../utils/OntologyUtils';

class RecordNavigationSection extends PureComponent {

  constructor(props) {
    super(props);
    this.state = {
      navigationExpanded: false,
      navigationQuery: ''
    };
  }

  render() {
    let { navigationExpanded, navigationQuery } = this.state;
    let { collapsedSections, heading } = this.props;
    let navigationQueryLower = navigationQuery.toLowerCase();
    let categoryWordsMap = makeCategoryWordsMap(this.props.categoryTree);
    let expandClassName = classnames({
      'wdk-RecordNavigationExpand fa': true,
      'fa-plus-square': !navigationExpanded,
      'fa-minus-square': navigationExpanded
    });

    return (
      <div className="wdk-RecordNavigationSection">
        <h2 className="wdk-RecordNavigationSectionHeader">
          <button className={expandClassName}
            onClick={() => void this.setState({ navigationExpanded: !navigationExpanded })}
          /> {heading}
        </h2>
        <div className="wdk-RecordNavigationSearch">
          <input
            className="wdk-RecordNavigationSearchInput"
            placeholder={'Search ' + heading}
            type="text"
            value={navigationQuery}
            onChange={e => {
              this.setState({
                navigationQuery: e.target.value,
                navigationExpanded: true
              });
            }}
          />
        </div>
        <div className="wdk-RecordNavigationCategories">
          <RecordNavigationSectionCategories
            record={this.props.record}
            recordClass={this.props.recordClass}
            categories={this.props.categoryTree.children}
            onSectionToggle={this.props.onSectionToggle}
            showChildren={navigationExpanded}
            isCollapsed={category => includes(collapsedSections, getId(category))}
            isVisible={category => includes(categoryWordsMap.get(category.properties), navigationQueryLower)}
          />
        </div>
      </div>
    );
  }
}

RecordNavigationSection.propTypes = {
  collapsedSections: React.PropTypes.array,
  onSectionToggle: React.PropTypes.func,
  heading: React.PropTypes.node
};

RecordNavigationSection.defaultProps = {
  onSectionToggle: function noop() {},
  heading: 'Contents'
};

export default wrappable(RecordNavigationSection);

let makeCategoryWordsMap = memoize((root) =>
  postorderSeq(root).reduce((map, node) => {
    let words = [];

    // add current node's displayName and description
    words.push(
      getDisplayName(node),
      ...getPropertyValues('hasDefinition', node),
      ...getPropertyValues('hasExactSynonym', node),
      ...getPropertyValues('hasNarrowSynonym', node)
    );

    // add displayName and desription of attribute or table
    if (nodeHasProperty('targetType', 'attribute', node) || nodeHasProperty('targetType', 'table', node)) {
      words.push(node.wdkReference.displayName, node.wdkReference.description);
    }

    // add words from any children
    for (let child of node.children) {
      words.push(map.get(child.properties));
    }

    return map.set(node.properties, words.join('\0').toLowerCase());
  }, new Map))
