import React from 'react';

import Icon from '../Components/Icon';
import PaginationUtils from '../Utils/PaginationUtils';
import PaginationEditor from '../Ui/PaginationEditor';
import { setPaginationAnchor } from '../State/Actions';

const settings = {
  overflowPoint: 8,
  innerRadius: 2
}

class PaginationMenu extends React.PureComponent {
  constructor (props) {
    super(props);
    this.renderPageLink = this.renderPageLink.bind(this);
    this.renderEllipsis = this.renderEllipsis.bind(this);
    this.renderPageList = this.renderPageList.bind(this);
    this.renderDynamicPageLink = this.renderDynamicPageLink.bind(this);
  }

  renderEllipsis (key = '') {
    return (
      <a key={'ellipsis-' + key} className="ellipsis">
        ...
      </a>
    );
  }

  renderPageLink (page, current) {
    let handler = () => this.goToPage(page);
    return (
      <a onClick={handler} key={page} className={current === page ? 'active' : 'inactive'}>
        {page}
      </a>
    );
  }

  getRelativePageNumber (relative) {
    const { list, paginationState } = this.props;

    switch (relative.toLowerCase()) {
      case 'first':
      case 'start':
        return 1;
      case 'last':
      case 'end':
        return PaginationUtils.totalPages(list, paginationState);
      case 'next':
        return PaginationUtils.nextPageNumber(list, paginationState);
      case 'prev':
      case 'previous':
        return PaginationUtils.prevPageNumber(list, paginationState);
      default:
        return null;
    }
  }

  getRelativeIcon (relative) {
    switch (relative.toLowerCase()) {
      case 'first':
      case 'start':
        return 'angle-double-left';
      case 'last':
      case 'end':
        return 'angle-double-right';
      case 'next':
        return 'caret-right';
      case 'prev':
      case 'previous':
        return 'caret-left';
      default:
        return null;
    }
  }

  goToPage (page) {
    let { paginationState, dispatch } = this.props;
    let anchorIndex = PaginationUtils.firstItemOnPage(page, paginationState);
    dispatch(setPaginationAnchor(anchorIndex));
  }

  renderRelativeLink (relative) {
    const page = this.getRelativePageNumber(relative);
    const icon = this.getRelativeIcon(relative);

    return (!page || !icon) ? null : (
      <a
        onClick={() => this.goToPage(page)}
        title={'Jump to the ' + relative + ' page'}
      >
        <Icon fa={icon} />
      </a>
    )
  }

  renderDynamicPageLink (page, current, total) {
    const link = this.renderPageLink(page, current);
    const dots = this.renderEllipsis(page);
    const { innerRadius } = settings;

    if (page === 1 || page === total) return link;
    if (page >= current - innerRadius && page <= current + innerRadius) return link;
    if (page === current - innerRadius - 1) return dots;
    if (page === current + innerRadius + 1) return dots;
    return null;
  }

  renderPageList () {
    const { paginationState, list } = this.props;
    const { overflowPoint } = settings;
    const current = PaginationUtils.getCurrentPageNumber(paginationState);
    const total = PaginationUtils.totalPages(list, paginationState);
    const pageList = PaginationUtils.generatePageList(total);

    if (total > overflowPoint) {
      return pageList.map(page => this.renderDynamicPageLink(page, current, total)).filter(el => el);
    } else {
      return pageList.map(page => this.renderPageLink(page, current));
    }
  }

  render () {
    const { list, paginationState, dispatch } = this.props;
    return !list.length ? null : (
      <div className="PaginationMenu">
        <span className="Pagination-Spacer" />
        <span className="Pagination-Nav">
          {this.renderRelativeLink('previous')}
        </span>
        <span className="Pagination-Nav">
          {this.renderPageList()}
        </span>
        <span className="Pagination-Nav">
          {this.renderRelativeLink('next')}
        </span>
        <span className="Pagination-Editor">
          <PaginationEditor paginationState={paginationState} dispatch={dispatch} />
        </span>
      </div>
    );
  }
};

export default PaginationMenu;
