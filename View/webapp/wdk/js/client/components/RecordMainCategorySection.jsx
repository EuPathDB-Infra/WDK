import { PropTypes } from 'react';
import RecordAttributeSection from './RecordAttributeSection';
import RecordTableSection from './RecordTableSection';
import CollapsibleSection from './CollapsibleSection';
import { PureComponent, wrappable } from '../utils/componentUtils';
import { getId, getTargetType, getDisplayName } from '../utils/CategoryUtils';

/**
 * Content for a node of a record category tree, or a record field.
 */
class RecordMainCategorySection extends PureComponent {
  constructor(props) {
    super(props);
    this.toggleCollapse = this.toggleCollapse.bind(this);
  }

  toggleCollapse() {
    let { category, onSectionToggle, isCollapsed, depth } = this.props;
    // only toggle non-top-level category and wdkReference nodes
    if ('wdkReference' in category || depth > 1) {
      onSectionToggle(
        getId(category),
        // It's tempting to negate this value, but we are sending the value
        // we want for isVisible here.
        isCollapsed
      );
    }
  }

  render() {
    let {
      record,
      recordClass,
      category,
      depth,
      isCollapsed,
      enumeration,
      children
    } = this.props;

    switch (getTargetType(category)) {
      case 'attribute': return (
        <RecordAttributeSection
          attribute={category.wdkReference}
          record={record}
          recordClass={recordClass}
          isCollapsed={isCollapsed}
          onCollapsedChange={this.toggleCollapse}
        />
      );

      case 'table': return (
        <RecordTableSection
          table={category.wdkReference}
          record={record}
          recordClass={recordClass}
          isCollapsed={isCollapsed}
          onCollapsedChange={this.toggleCollapse}
        />
      )

      default: {
        let id = getId(category);
        let categoryName = getDisplayName(category);
        let Header = 'h' + Math.min(depth + 1, 6);
        let headerContent = (
          <span>
            <span className="wdk-RecordSectionEnumeration">{enumeration}</span> {categoryName}
            <a className="wdk-RecordSectionLink" onClick={e => e.stopPropagation()} href={'#' + id}>&sect;</a>
          </span>
        );
        return (
          <CollapsibleSection
            id={id}
            className={depth === 1 ? 'wdk-RecordSection' : 'wdk-RecordSubsection'}
            headerComponent={Header}
            headerContent={headerContent}
            isCollapsed={isCollapsed}
            onCollapsedChange={this.toggleCollapse}
          >
            {children}
          </CollapsibleSection>
        );
      }
    }
  }
}

RecordMainCategorySection.propTypes = {
  category: PropTypes.object.isRequired,
  depth: PropTypes.number.isRequired,
  enumeration: PropTypes.string.isRequired,
  isCollapsed: PropTypes.bool.isRequired,
  onSectionToggle: PropTypes.func.isRequired,
  record: PropTypes.object.isRequired,
  recordClass: PropTypes.object.isRequired,
  children: PropTypes.element
};

export default wrappable(RecordMainCategorySection);
