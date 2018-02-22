import $ from 'jquery';
import { eq, uniqueId, once } from 'lodash';
import React, {Component, PureComponent, ReactElement} from 'react';
import {unstable_renderSubtreeIntoContainer, unmountComponentAtNode} from 'react-dom';
import {formatAttributeValue, lazy, wrappable} from '../utils/componentUtils';
import { containsAncestorNode } from '../utils/DomUtils';
import RealTimeSearchBox from './RealTimeSearchBox';

const expandColumn = {
  data: undefined,
  className: 'wdk-DataTableCell wdk-DataTableCell__childRowToggle',
  orderable: false,
  title: '<button class="wdk-DataTableCellExpand"></button>',
  defaultContent: '<div class="wdk-DataTableCellExpand"></div>'
};

type ColumnDef = {
  name: string;
  displayName: string;
  help?: string;
  sortType?: string;
  isDisplayable?: boolean;
  isSortable?: boolean;
}

type Row = { [key: string]: any };

type SortingDef = {
  name: string;
  direction: 'ASC' | 'DESC';
}

type ChildRowProps = {
  rowIndex: number;
  rowData: Object;
}

type Props = {
  /**
   * Array of descriptors used for table column headers.
   * `name` is used as a key for looking up cell data in `data`.
   * `displayName` is an optional property used for the header text (`name` is
   * used as a fallback).
   */
  columns: ColumnDef[];

  /**
   * The data to display in the table. An array of objects whose keys correspond
   * to the `name` property in the `columns` prop.
   */
  data: Row[];

  /**
   * Default sorting for the table. Each item indicates the column and direction.
   */
  sorting?: SortingDef[];

  onSortingChange?: (sorting: SortingDef[]) => void;

  /** width of the table - if a string, treated as a CSS unit; if a number, treated as px */
  width?: string | number;

  /** height of the table - if a string, treated as a CSS unit; if a number, treated as px */
  height?: string | number;

  /** can users search the table */
  searchable?: boolean;

  /**
   * Determines the body of child rows. If this is provided, each table row will
   * be rendered with an expansion toggle. This can be a string, a function, or
   * a React Component. If it is a function, the function will receive the same
   * props argument that the React Component would receive as props:
   *
   *    props: {
   *      rowIndex: number;
   *      rowData: Object;
   *    }
   */
  childRow?: string | ((props: ChildRowProps) => ReactElement<ChildRowProps>);

  /** Array of row ids that should be expanded */
  expandedRows?: number[];

  /** Called when expanded rows change */
  onExpandedRowsChange?: (indexes: number[]) => void;

  getRowId: (row: Row) => number;
}

/**
 * Sortable table for WDK-formatted data.
 *
 * This uses DataTables jQuery plugin
 */
class DataTable extends PureComponent<Props> {

  /** Default DataTables jQuery plugin options. */
  static defaultDataTableOpts = {
    dom: 'lript',
    autoWidth: false,
    deferRender: true,
    paging: false,
    searching: true,
    language: {
      info: 'Showing _TOTAL_ ',
      infoFiltered: 'of _MAX_ ',
      infoEmpty: 'Showing 0 ',
      infoPostFix: 'rows'
    }
  };

  _childRowContainers: Map<HTMLTableRowElement, HTMLElement>;

  _isRedrawing: boolean;

  _dataTable: DataTables.Api;

  _table: HTMLElement;

  _searchTerm = '';

  node: HTMLElement | null;

  columns: DataTables.ColumnSettings[];

  componentDidMount() {
    this._childRowContainers = new Map();
    this._setup();
  }

  componentDidUpdate(prevProps: Props) {
    let columnsChanged = didPropChange(this, prevProps, 'columns')
    let dataChanged = didPropChange(this, prevProps, 'data');
    let childRowChanged = didPropChange(this, prevProps, 'childRow');
    let sortingChanged = didPropChange(this, prevProps, 'sorting') &&
      eq(this.props.sorting, prevProps.sorting);
    let widthChanged = didPropChange(this, prevProps, 'width');
    let heightChanged = didPropChange(this, prevProps, 'height');
    let expandedRowsChanged = didPropChange(this, prevProps, 'expandedRows');

    this._isRedrawing = true;

    if (columnsChanged || dataChanged || heightChanged) {
      this._destroy();
      this._setup();
    }

    else {
      let needsRedraw = false;

      if (childRowChanged) {
        this._rerenderChildRows();
        needsRedraw = true;
      }

      if (sortingChanged) {
        this._updateSorting();
        needsRedraw = true;
      }

      if (widthChanged) {
        this._updateWidth();
        needsRedraw = true;
      }

      if (expandedRowsChanged) {
        this._updateExpandedRows();
        // needsRedraw = true;
      }

      if (needsRedraw) {
        this._dataTable.draw();
      }
    }

    this._isRedrawing = false;
  }

  componentWillUnmount() {
    this._destroy();
  }


  // prop change handlers
  // --------------------

  /** Initialize datatable plugin and set up handlers for creating child rows */
  _setup() {
    if (this.node == null) return;

    let {
      childRow,
      data,
      sorting = [],
      searchable = true,
      height,
      width
    } = this.props;

    let columns = this.columns = childRow != null
      ? [ expandColumn, ...formatColumns(this.props.columns) ]
      : formatColumns(this.props.columns);

    let order = formatSorting(columns, sorting.length === 0
      ? [ { name: this.props.columns[0].name, direction: 'ASC' } ] : sorting);

    let tableOpts = Object.assign({}, DataTable.defaultDataTableOpts, {
      columns,
      data,
      order,
      searching: searchable,
      info: searchable,
      headerCallback: once((thead: HTMLTableHeaderCellElement) => {
        const $ths = $(thead).find('th');
        const offset = childRow ? 1 : 0;
        if (childRow) {
          $ths.eq(0).attr('title', 'Show or hide all row details');

        }
        this.props.columns
        .filter(column => column.isDisplayable)
        .forEach((column, index) => {
          if (column.help != null) {
            $ths.eq(index + offset)
              .append('&nbsp;')
              .append($(`
                <div class="HelpTrigger">
                  <i class="fa fa-question-circle"></i>
                </div>
              `.trim())
                .attr('title', column.help)
                .click(e => e.stopPropagation())
                .qtip({
                  hide: {
                    fixed: true,
                    delay: 500
                  },
                  style: {
                    classes: 'qtip-wdk'
                  }
                }));
          }
        });
      }),
      createdRow: (row: HTMLTableRowElement) => {
        row.classList.add('wdk-DataTableRow');
        if (childRow) {
          row.classList.add('wdk-DataTableRow__expandable');
          row.tabIndex = 0;
        }
      }
    });

    if (height != null)
      Object.assign(tableOpts, {
        scrollY: height,
        scrollX: true,
        scrollCollapse: !childRow
      });

    this._dataTable = $(this._table = document.createElement('table'))
    .addClass('wdk-DataTable')
    .width(width || '')
    .appendTo(this.node)
    // click handler for expand single row
    .on('click keydown', '.wdk-DataTableRow__expandable', event => {

      // ignore keydown events if the key is not Enter
      if (event.type === 'keydown' && event.key !== 'Enter') {
        return;
      }

      let tr = event.currentTarget;

      // ignore event if a link, button, or input element is clicked
      if (containsAncestorNode(
        event.target,
        node => $(node).is('a,:button,:input'),
        tr)) {
        return;
      }

      // ignore event if text has been selected
      const selection = window.getSelection();
      const selectionText = selection.toString();
      if (
        selectionText &&
        containsAncestorNode(
          selection.anchorNode,
          currNode => currNode.parentNode === tr
        )) {
        return;
      }

      let row = this._dataTable.row(tr);
      if (row.child.isShown()) {
        this._hideChildRow(row.node() as HTMLTableRowElement);
      }
      else {
        this._renderChildRow(row.node() as HTMLTableRowElement);
      }
      this._updateChildRowClassNames();
      this._callExpandedRowsCallback();
    })
    // click handler for expand all rows
    .on('click', 'th .wdk-DataTableCellExpand', event => {
      // if all are shown, then hide all, otherwise show any that are hidden
      let allShown = areAllChildRowsShown(this._dataTable);
      let update = allShown ? this._hideChildRow : this._renderChildRow;
      for (let tr of this._dataTable.rows().nodes().toArray()) {
        update.call(this, tr);
      }
      this._updateChildRowClassNames();
      this._callExpandedRowsCallback();
    })
    .on('order.dt', () => {
      if (this._isRedrawing || !this.props.onSortingChange || !this._dataTable) return;

      let sorting = this._dataTable.order().map(entry => ({
        name: columns[entry[0] as number].data as string,
        direction: (entry[1] as string).toUpperCase() as 'ASC' | 'DESC'
      }));
      this.props.onSortingChange(sorting);
    })
    .DataTable(tableOpts);

    if (childRow != null) {
      this._updateExpandedRows();
      this._dataTable.draw();
    }
  }

  _rerenderChildRows() {
    for (let tableRowNode of this._childRowContainers.keys()) {
      this._renderChildRow(tableRowNode, false);
    }
  }

  _updateSorting() {
    this._dataTable.order(formatSorting(this.columns, this.props.sorting));
  }

  _updateWidth() {
    $(this._table).width(this.props.width || '');
    this._dataTable.columns.adjust();
  }

  _updateExpandedRows() {
    let { getRowId, expandedRows = [] } = this.props;

    this._dataTable.rows().every((index) => {
      let row = this._dataTable.row(index);
      let tr = row.node() as HTMLTableRowElement;
      let data = row.data() as Row;
      if (expandedRows.includes(getRowId(data))) {
        this._renderChildRow(tr);
      }
      else {
        this._hideChildRow(tr);
      }
    });
    this._updateChildRowClassNames();
  }

  /** Update class names of child row expand buttons based on datatable state */
  _updateChildRowClassNames() {
    let allShown = true;
    for (let tr of this._dataTable.rows().nodes().toArray()) {
      let row = this._dataTable.row(tr);
      let isShown = Boolean(row.child.isShown());
      $(tr).toggleClass('wdk-DataTableRow__expanded', isShown);
      allShown = allShown && isShown;
    }
    $(this._table)
      .find('th .wdk-DataTableCellExpand')
      .closest('tr')
      .toggleClass('wdk-DataTableRow__expanded', allShown);
  }

  /** Append child row container node to table row and show it */
  _renderChildRow(tableRowNode: HTMLTableRowElement, openRow = true) {
    let { childRow } = this.props;
    if (childRow == null) return;
    let row = this._dataTable.row(tableRowNode);
    let childRowContainer = this._getChildRowContainer(tableRowNode);
    if (row.child() == null) row.child(childRowContainer);
    if (typeof childRow === 'string') {
      childRowContainer.innerHTML = childRow;
    }
    else {
      let props = { rowIndex: row.index(), rowData: row.data() };
      unstable_renderSubtreeIntoContainer(this, React.createElement(childRow, props), childRowContainer);
    }
    if (openRow && !row.child.isShown()) {
      row.child.show();
      tableRowNode.setAttribute('aria-expanded', 'true');
    }
  }

  /** Hide child row */
  _hideChildRow(tableRowNode: HTMLTableRowElement) {
    let row = this._dataTable.row(tableRowNode);
    row.child.hide();
      tableRowNode.setAttribute('aria-expanded', 'false');
  }

  _callExpandedRowsCallback() {
    let { onExpandedRowsChange } = this.props;
    if (onExpandedRowsChange == null) return;

    let expandedRows =  this._dataTable.rows().indexes().toArray()
      .reduce((expandedRows, index) => {
        let row = this._dataTable.row(index);
        if (row.child.isShown()) {
          expandedRows.push(this.props.getRowId(row.data() as Row));
        }
        return expandedRows;
      }, []);
    onExpandedRowsChange(expandedRows);
  }

  /** Get child row container from cache, or create and add to cache first */
  _getChildRowContainer(tableRowNode: HTMLTableRowElement) {
    if (!this._childRowContainers.has(tableRowNode)) {
      const container = document.createElement('div');
      container.id = uniqueId('DataTableChildRow');
      tableRowNode.setAttribute('aria-controls', container.id);
      this._childRowContainers.set(tableRowNode, container);
    }
    return this._childRowContainers.get(tableRowNode) as HTMLElement;
  }

  /** Unmount all child row components and destroy the datatable instance */
  _destroy() {
    this._dataTable.destroy(true);
    for (let container of this._childRowContainers.values()) {
      unmountComponentAtNode(container);
    }
    this._childRowContainers.clear();
  }

  render() {
    let { searchable = true } = this.props;
    return (
      <div className="MesaComponent">
        {searchable && (
          <RealTimeSearchBox
            searchTerm={this._searchTerm}
            className="wdk-DataTableSearchBox"
            placeholderText="Search this table..."
            onSearchTermChange={(searchTerm: string) => {
              this._searchTerm = searchTerm;
              this._dataTable.search(searchTerm).draw();
            }}
            delayMs={0}
          />
        )}
        <div ref={node => this.node = node} className="wdk-DataTableContainer"/>
      </div>
    );
  }

}

const withLibs = lazy(function(render) {
  require([ 'lib/jquery-datatables', 'lib/jquery-datatables-natural-type-plugin' ], render);
});
export default wrappable(withLibs(DataTable));


// helpers
// -------

/** helper to determine if all child rows are visible */
function areAllChildRowsShown(dataTable: DataTables.Api) {
  return dataTable.rows().indexes().toArray().every((i: number) => !!dataTable.row(i).child.isShown());
}

/** Map WDK table attribute fields to datatable data format */
function formatColumns(columns: ColumnDef[]): DataTables.ColumnSettings[] {
  return columns.map(
    column => ({
      data: column.name,
      className: 'wdk-DataTableCell wdk-DataTableCell__' + column.name,
      title: column.displayName || column.name,
      type: column.sortType || 'natural-ci',
      visible: column.isDisplayable,
      searchable: column.isDisplayable,
      orderable: column.isSortable,
      render(data: any, type: string) {
        let value = formatAttributeValue(data);
        if (type === 'display' && value != null) {
          return '<div class="wdk-DataTableCellContent">' + value + '</div>'
        }
        return value || '';
      }
    })
  );
}

/** Map WDK table sorting to datatable data format */
function formatSorting(columns: DataTables.ColumnSettings[], sorting: SortingDef[] = []) {
  return sorting.length === 0 ? [ [0, 'asc'] ] : sorting.map(sort => {
    let index = columns.findIndex(column => column.data === sort.name);
    if (index === -1) {
      console.warn("Could not determine sort index for the column " + sort.name);
      return [];
    }
    return [ index, sort.direction.toLowerCase() ]
  });
}

/** Return boolean indicating if a prop's value has changed. */
function didPropChange(component: Component<Props, any>, prevProps: Props, propName: string) {
  return (component.props as any)[propName] !== (prevProps as any)[propName];
}
