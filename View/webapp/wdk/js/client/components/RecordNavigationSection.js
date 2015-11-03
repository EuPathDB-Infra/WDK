import React from 'react';
import classnames from 'classnames';
import RecordNavigationSectionCategories from './RecordNavigationSectionCategories';
import { wrappable } from '../utils/componentUtils';

let RecordNavigationSection = React.createClass({

  propTypes: {
    categories: React.PropTypes.array,
    collapsedCategories: React.PropTypes.array,
    onCategoryToggle: React.PropTypes.func,
    heading: React.PropTypes.node
  },

  mixins: [ React.addons.PureRenderMixin ],

  getInitialState() {
    // TODO Move state into RecordViewStore
    return {
      navigationExpanded: false,
      navigationQuery: ''
    };
  },

  getDefaultProps() {
    return {
      onCategoryToggle: function noop() {},
      heading: 'Categories'
    };
  },

  render() {
    let { categories, collapsedCategories, heading } = this.props;
    let { navigationExpanded, navigationQuery } = this.state;

    let expandClassName = classnames({
      'fa': true,
      'fa-plus-square': !navigationExpanded,
      'fa-minus-square': navigationExpanded
    });

    return (
      <div className="wdk-RecordNavigationSection">
        <div className="wdk-RecordNavigationSearch">
          <input
            className="wdk-RecordNavigationSearchInput"
            placeholder={'Search ' + heading}
            type="text"
            value={navigationQuery}
            onChange={e => this.setState({
              navigationQuery: e.target.value,
              navigationExpanded: true
            })}
          />
        </div>
        <h2 className="wdk-RecordNavigationSectionHeader">
          <i className={expandClassName}
            onClick={() => this.setState({ navigationExpanded: !navigationExpanded })}
          /> {heading}
        </h2>
        <div className="wdk-RecordNavigationCategories">
          <RecordNavigationSectionCategories
            categories={categories}
            collapsedCategories={collapsedCategories}
            onCategoryToggle={this.props.onCategoryToggle}
            expanded={navigationExpanded}
            query={navigationQuery.trim().toLowerCase()}
          />
        </div>
      </div>
    );
  }
});

export default wrappable(RecordNavigationSection);
