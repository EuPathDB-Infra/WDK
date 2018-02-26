import React from 'react';
import PropTypes from 'prop-types';
import Loading from '../../Loading';
import EmptyField from './EmptyField';
import SingleFieldFilter from './SingleFieldFilter';
import MultiFieldFilter from './MultiFieldFilter';

/**
 * Main interactive filtering interface for a particular field.
 */
export default function FieldFilter(props) {
  let className = 'field-detail';
  if (props.hideFieldPanel) className += ' ' + className + '__fullWidth';

  return (
    <div className={className}>
      {!props.activeField ? (
        <EmptyField displayName={props.displayName}/>
      ) : (
        <div>
          <h3>
            {props.activeField.display + ' '}
          </h3>
          {props.activeField.description && (
            <div className="field-description">{props.activeField.description}</div>
          )}
          {props.activeFieldState && props.activeFieldState.errorMessage ? (
            <div style={{ color: 'darkred' }}>{props.activeFieldState.errorMessage}</div>
          ) : (props.activeFieldSummary == null || props.dataCount == null) ? (
            <Loading />
          ) : ( props.activeField.isMulti
            ? <MultiFieldFilter {...props} />
            : <SingleFieldFilter {...props} />
          )}
        </div>
      )}
    </div>
  );
}

const FieldSummary = PropTypes.shape({
  valueCounts: PropTypes.array.isRequired,
  internalsCount: PropTypes.number.isRequired,
  internalsFilteredCount: PropTypes.number.isRequired
});

const MultiFieldSummary = PropTypes.arrayOf(PropTypes.shape({
  term: PropTypes.string.isRequired,
  valueCounts: PropTypes.array.isRequired,
  internalsCount: PropTypes.number.isRequired,
  internalsFilteredCount: PropTypes.number.isRequired
}));

FieldFilter.propTypes = {
  displayName: PropTypes.string,
  dataCount: PropTypes.number,
  filteredDataCount: PropTypes.number,
  filters: PropTypes.array,
  activeField: PropTypes.object,
  activeFieldState: PropTypes.object,
  activeFieldSummary: PropTypes.oneOfType([FieldSummary, MultiFieldSummary]),

  onFiltersChange: PropTypes.func,
  onMemberSort: PropTypes.func,
  onMemberSearch: PropTypes.func,
  onRangeScaleChange: PropTypes.func,

  hideFieldPanel: PropTypes.bool,
  selectByDefault: PropTypes.bool.isRequired
};

FieldFilter.defaultProps = {
  displayName: 'Items'
}
