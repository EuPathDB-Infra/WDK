import { bindAll, difference, escapeRegExp, get, has, isFunction, memoize, debounce } from 'lodash';
import { MesaController as Mesa } from 'mesa';
import React from 'react';

import { safeHtml } from 'Utils/ComponentUtils';
import { findAncestorNode } from 'Utils/DomUtils';
import RealTimeSearchBox from 'Components/SearchBox/RealTimeSearchBox';
import Toggle from 'Components/Icon/Toggle';
import StackedBar from './StackedBar';
import FilterLegend from './FilterLegend';

const UNKNOWN_ELEMENT = <em>Not specified</em>;

class MembershipField extends React.PureComponent {

  constructor(props) {
    super(props);
    this.state = {};
    this.handleMouseOver = this.handleMouseOver.bind(this);
    this.handleMouseLeave = this.handleMouseLeave.bind(this);
    this.handleGroupBySelected = this.handleGroupBySelected.bind(this);
    this.mapMouseTargetToTooltipState = debounce(this.mapMouseTargetToTooltipState, 250);
    this.state = { showDisabledTooltip: false };
  }

  componentDidCatch(error, info) {
    this.setState({ hasError: true });
    console.log({ error, info });
  }

  handleMouseOver(event) {
    const { target, originalTarget } = event;
    this.mapMouseTargetToTooltipState(target, originalTarget);
  }

  handleMouseLeave() {
    this.setState({ showDisabledTooltip: false, top: undefined, left: undefined })
    this.mapMouseTargetToTooltipState.cancel();
  }

  handleGroupBySelected() {
    this.props.onSort(
      this.props.field,
      Object.assign({}, this.props.fieldState.sort, {
        groupBySelected: !this.props.fieldState.sort.groupBySelected
      })
    );
  }

  isSortEnabled() {
    return (
      has(this.props, 'fieldState.sort') &&
      isFunction(this.props.onSort)
    );
  }

  mapMouseTargetToTooltipState(element, root) {
    const disabledRow = findAncestorNode(
      element,
      isDisabledRow,
      root
    );
    const showDisabledTooltip = disabledRow != null;
    const { top, left } = disabledRow == null ? {} : disabledRow.getBoundingClientRect();

    this.setState({
      showDisabledTooltip,
      tooltipTop: top + 20,
      tooltipLeft: left + 40
    })
  }

  render() {
    if (this.state.hasError) return <div>Something when wrong (check the console)</div>

    const unknownCount = this.props.dataCount - this.props.fieldSummary.internalsCount;
    const unknownMessage = unknownCount > 0 &&
      <div className="membership-action membership-action__unknown-count">
        <strong>{unknownCount} {this.props.displayName} have no data for this quality</strong>
      </div>;

    return (
      <div className="membership-filter" onMouseOver={this.handleMouseOver} onMouseLeave={this.handleMouseLeave}>
        {this.props.filter == null ? (
          <div className="membership-actions">
            <div className="membership-action__no-filters">
              <em>Check items below to apply this filter</em>
            </div>
            {unknownMessage}
          </div>
        )
          : this.isSortEnabled() ? (
            <div className="membership-actions">
              <div className="membership-action membership-action__group-selected">
                <button
                  style={{
                    background: 'none',
                    border: 'none',
                    padding: 0
                  }}
                  type="button"
                  onClick={this.handleGroupBySelected}
                >
                  <Toggle
                    on={this.props.fieldState.sort.groupBySelected}
                  /> Keep checked values at top
                </button>
              </div>
              {unknownMessage}
            </div>
          ) : null}

        {this.state.showDisabledTooltip &&
          <div
            className="disabled-tooltip"
            style={{
              left: this.state.tooltipLeft,
              top: this.state.tooltipTop
            }}
          >
            This item is unavailable because your previous filters have removed these {this.props.displayName}.
          </div>
        }

        <MembershipTable {...this.props} />
      </div>
    )
  }

}

/**
 * Membership field component
 */
class MembershipTable extends React.PureComponent {
  static getHelpContent(props) {
    var displayName = props.displayName;
    var fieldDisplay = props.field.display;
    return (
      <div>
        You may add or remove {displayName} with specific {fieldDisplay} values
        from your overall selection by checking or unchecking the corresponding
        checkboxes.
      </div>
    );
  }

  constructor(props) {
    super(props);
    bindAll(this,
      'deriveRowClassName',
      'handleRemoveAll',
      'handleRowClick',
      'handleRowDeselect',
      'handleRowSelect',
      'handleSearchTermChange',
      'handleSelectAll',
      'handleSort',
      'isItemSelected',
      'renderCheckboxCell',
      'renderCheckboxHeading',
      'renderDistributionCell',
      'renderFilteredCountCell',
      'renderFilteredCountHeading1',
      'renderFilteredCountHeading2',
      'renderPrecentageCell',
      'renderUnfilteredCountCell',
      'renderUnfilteredCountHeading1',
      'renderUnfilteredCountHeading2',
      'renderValueCell',
      'renderValueHeading',
      'renderValueHeadingSearch',
      'toFilterValue',
    );
    this.getKnownValues = memoize(this.getKnownValues);
    this.isItemSelected = memoize(this.isItemSelected);
  }

  componentWillReceiveProps(nextProps) {
    if (this.props.fieldSummary !== nextProps.fieldSummary) {
      this.getKnownValues.cache.clear();
    }
    if (this.props.filter !== nextProps.filter) {
      this.isItemSelected.cache.clear();
    }
  }

  toFilterValue(value) {
    return this.props.field.type === 'string' ? String(value)
      : this.props.field.type === 'number' ? Number(value)
      : this.props.field.type === 'date' ? Date(value)
      : value;
  }

  getKnownValues() {
    return this.props.fieldSummary.valueCounts
      .filter(({ value }) => value != null)
      .map(({ value }) => value);
  }

  getValuesForFilter() {
    return get(this.props, 'filter.value');
  }

  getFilteredRows(searchTerm) {
    let re = new RegExp(escapeRegExp(searchTerm), 'i');
    return searchTerm !== ''
      ? this.props.fieldSummary.valueCounts.filter(entry => re.test(entry.value))
      : this.props.fieldSummary.valueCounts;
  }

  deriveRowClassName(item) {
    const selectedClassName = (
      item.filteredCount > 0 &&
      (this.props.filter == null || this.isItemSelected(item))
    ) ? 'member__selected' : '';

    const disabledClassName = item.filteredCount === 0
      ? 'member__disabled' : '';

    return `member ${selectedClassName} ${disabledClassName}`;
  }

  isItemSelected(item) {
    let { filter, selectByDefault } = this.props;

    return filter == null ? selectByDefault
      // value is null (ie, unknown) and includeUnknown selected
      : item.value == null ? filter.includeUnknown
      // filter.value is null (ie, all known values), or filter.value includes value
      : filter.value == null || filter.value.includes(item.value);
  }

  isSortEnabled() {
    return (
      has(this.props, 'fieldState.sort') &&
      isFunction(this.props.onSort)
    );
  }

  isSearchEnabled() {
    return (
      this.props.fieldSummary.valueCounts.length > 10 &&
      has(this.props, 'fieldState.searchTerm') &&
      isFunction(this.props.onSearch)
    );
  }

  handleItemClick(item, addItem = !this.isItemSelected(item)) {
    let { selectByDefault } = this.props;
    let { value, filteredCount } = item;

    if (filteredCount === 0) {
      // Don't do anything since item is "disabled"
      return;
    }

    if (value == null) {
      this.handleUnknownChange(addItem);
    }
    else {
      const currentFilterValue = this.props.filter == null
        ? (selectByDefault ? this.getKnownValues() : [])
        : this.getValuesForFilter() || this.getKnownValues();
      const filterValue = addItem
        ? currentFilterValue.concat(value)
        : currentFilterValue.filter(v => v !== value);

      this.emitChange(filterValue.length === this.getKnownValues().length
        ? undefined
        : filterValue);
    }
  }

  handleRowClick(item) {
    this.handleItemClick(item);
  }

  handleRowSelect(item) {
    this.handleItemClick(item, true);
  }

  handleRowDeselect(item) {
    this.handleItemClick(item, false);
  }

  handleUnknownChange(addUnknown) {
    this.emitChange(this.getValuesForFilter(), addUnknown);
  }

  handleSelectAll() {
    const allValues = this.getKnownValues();

    const disabledValues = this.props.fieldSummary.valueCounts
      .filter(entry => entry.filteredCount === 0)
      .map(entry => entry.value);

    const filterValues = this.getValuesForFilter();

    const value = this.isSearchEnabled()
      ? difference(
        this.getFilteredRows(this.props.fieldState.searchTerm)
          .map(entry => entry.value),
        disabledValues
      ).concat(filterValues || [])
      : ( disabledValues.length === 0 && filterValues == null
        ? undefined
        : difference(allValues, disabledValues).concat(filterValues || []));

    this.emitChange(value);
  }

  handleRemoveAll() {
    this.emitChange([]);
  }

  handleSort({ key: columnKey }, direction) {
    let nextSort = { columnKey, direction };
    let sort = Object.assign({}, this.props.fieldState.sort, nextSort);
    this.props.onSort(this.props.field, sort);
  }

  handleSearchTermChange(searchTerm) {
    this.props.onSearch(this.props.field, searchTerm);
  }

  emitChange(value, includeUnknown = get(this.props, 'filter.includeUnknown', false)) {
    this.props.onChange(this.props.field, value, includeUnknown,
      this.props.fieldSummary.valueCounts);
  }

  renderCheckboxHeading() {
    const availableItems = this.props.fieldSummary.valueCounts
      .filter(member => member.filteredCount > 0);
    const allAvailableChecked = availableItems
      .every(member => this.isItemSelected(member));
    const onClick = () =>
      allAvailableChecked ? this.handleRemoveAll() : this.handleSelectAll()
    return (
      <input
        type="checkbox"
        disabled={availableItems.length === 0}
        checked={availableItems.length > 0 && allAvailableChecked}
        onChange={onClick} />
    );
  }

  renderCheckboxCell({ row }) {
    const isChecked = this.isItemSelected(row);
    const isDisabled = row.filteredCount === 0;
    const onClick = () =>
      isChecked ? this.handleRowDeselect(row) : this.handleRowSelect(row);
    return (
      <input type="checkbox" checked={isChecked} onChange={onClick} disabled={isDisabled} />
    );
  }

  renderValueHeading() {
    return this.props.field.display;
  }

  renderValueHeadingSearch() {
    return (
      <div
        style={{
          width: '15em',
          fontSize: '.8em',
          fontWeight: 'normal',
        }}
        onMouseUp={event => {
          event.stopPropagation();
        }}
      >
        <RealTimeSearchBox
          searchTerm={this.props.fieldState.searchTerm}
          placeholderText="Find items"
          onSearchTermChange={this.handleSearchTermChange}
        />
      </div>
    );
  }

  renderValueCell({ value }) {
    return (
      <div>{value == null ? UNKNOWN_ELEMENT : safeHtml(String(value))}</div>
    );
  }

  renderCountHeading1(qualifier) {
    return (
      <div style={{display: 'flex', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
        <div>{qualifier}</div>
        <div style={{marginLeft: '.6ex', maxWidth: '6em', overflow: 'hidden', textOverflow: 'ellipsis'}}>{this.props.displayName}</div>
      </div>
    );
  }

  renderCountHeading2(internalsCount) {
    return (
      <div>
        {internalsCount.toLocaleString()}
        <small style={{ display: 'inline-block', width: '50%', textAlign: 'center' }}>(100%)</small>
      </div>
    );
  }

  renderCountCell(value, internalsCount) {
    return (
      <div>
        {value.toLocaleString()}
        &nbsp;
        {internalsCount != null && (
          <small style={{ display: 'inline-block', width: '50%', textAlign: 'center' }}>
            ({value === 0 || internalsCount ===  0 ? 0 : Math.round(value/internalsCount * 100)}%)
          </small>
        )}
      </div>
    );
  }

  renderFilteredCountHeading1() {
    return this.renderCountHeading1('Remaining');
  }

  renderFilteredCountHeading2() {
    return this.renderCountHeading2(this.props.fieldSummary.internalsFilteredCount);
  }

  renderFilteredCountCell({ value }) {
    return this.renderCountCell(value, this.props.fieldSummary.internalsFilteredCount);
  }

  renderUnfilteredCountHeading1() {
    return this.renderCountHeading1('All');
  }

  renderUnfilteredCountHeading2() {
    return this.renderCountHeading2(this.props.fieldSummary.internalsCount);
  }

  renderUnfilteredCountCell({ value }) {
    return this.renderCountCell(value, this.props.fieldSummary.internalsCount);
  }

  renderDistributionCell({ row }) {
    return (
      <StackedBar
        count={row.count}
        filteredCount={row.filteredCount}
        populationSize={this.props.fieldSummary.internalsCount || this.props.dataCount}
      />
    );
  }

  renderPrecentageCell({ row }) {
    return (
      <small title={`Remaining "${row.value}" / All "${row.value}"`}>
        ({Math.round(row.filteredCount / row.count * 100)}%)
      </small>
    );
  }

  render() {
    var useSort = this.isSortEnabled();
    var useSearch = this.isSearchEnabled();

    var rows = useSearch
      ? this.getFilteredRows(this.props.fieldState.searchTerm)
      : this.props.fieldSummary.valueCounts;

    return (
      <Mesa
        options={{
          // isRowSelected: this.isItemSelected,
          deriveRowClassName: this.deriveRowClassName,
          onRowClick: this.handleRowClick,
          useStickyHeader: true,
          tableBodyMaxHeight: '80vh'
        }}
        uiState={this.props.fieldState}
        actions={[]}
        eventHandlers={{
          // onRowSelect: this.handleRowSelect,
          // onRowDeselect: this.handleRowDeselect,
          // onMultipleRowSelect: this.handleSelectAll,
          // onMultipleRowDeselect: this.handleRemoveAll,
          onSort: this.handleSort
        }}
        rows={this.props.fieldSummary.valueCounts}
        filteredRows={rows}
        columns={[
          {
            key: 'checked',
            sortable: false,
            width: '32px',
            renderHeading: this.renderCheckboxHeading,
            renderCell: this.renderCheckboxCell
          },
          {
            key: 'value',
            inline: true,
            sortable: useSort,
            wrapCustomHeadings: ({ headingRowIndex }) => headingRowIndex === 0,
            renderHeading: useSearch
              ? [this.renderValueHeading, this.renderValueHeadingSearch]
              : this.renderValueHeading,
            renderCell: this.renderValueCell
          },
          {
            key: 'filteredCount',
            sortable: useSort,
            width: '11em',
            helpText: (
              <div>
                The number of <em>{this.props.displayName}</em> that match the criteria chosen for other qualities, <br />
                and that have the given <em>{this.props.field.display}</em> value.
                </div>
            ),
            wrapCustomHeadings: ({ headingRowIndex }) => headingRowIndex === 0,
            renderHeading: this.props.fieldSummary.internalsFilteredCount != null
              ? [this.renderFilteredCountHeading1, this.renderFilteredCountHeading2]
              : this.renderFilteredCountHeading1,
            renderCell: this.renderFilteredCountCell
          },
          {
            key: 'count',
            sortable: useSort,
            width: '11em',
            helpText: (
              <div>
                The number of <em>{this.props.displayName}</em> with the
                  given <em>{this.props.field.display}</em> value.
                </div>
            ),
            wrapCustomHeadings: ({ headingRowIndex }) => headingRowIndex === 0,
            renderHeading: this.props.fieldSummary.internalsCount != null
              ? [this.renderUnfilteredCountHeading1, this.renderUnfilteredCountHeading2]
              : this.renderUnfilteredCountHeading1,
            renderCell: this.renderUnfilteredCountCell
          },
          {
            key: 'distribution',
            name: 'Distribution',
            width: '30%',
            helpText: <FilterLegend {...this.props} />,
            renderCell: this.renderDistributionCell
          },
          {
            key: '%',
            name: '',
            width: '4em',
            helpText: (
              <div>
                <em>Remaining {this.props.displayName}</em> out of <em>Total {this.props.displayName}</em><br />
                with the given <em>{this.props.field.display}</em> value.
                </div>
            ),
            renderCell: this.renderPrecentageCell
          }
        ]}
      >
      </Mesa>
    );
  }
}

export default MembershipField

/** @param {HTMLElement} element */
function isDisabledRow(element) {
  return element.classList.contains('member__disabled');
}
