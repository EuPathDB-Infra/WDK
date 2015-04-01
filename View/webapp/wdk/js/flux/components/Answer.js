import throttle from 'lodash/function/throttle';
import React from 'react';
import RecordTable from './RecordTable';
import RecordList from './RecordList';

// Assign global jQuery to local variable
const $ = window.jQuery;


// Calculate the offset of `node` relative to the top of the document.
const getOffsetTop = (node, sum = 0) => node.offsetTop === 0
  ? sum
  : getOffsetTop(node.offsetParent, sum + node.offsetTop);


const Answer = React.createClass({

  getInitialState() {
    return {
      height: 0
    };
  },

  componentDidMount() {
    this._updateHeight();
    $(window).on('resize', this._updateHeight);
  },

  componentWillUnmount() {
    $(window).off('resize', this._updateHeight);
  },

  _updateHeight() {
    if (this.refs.records) {
      const node = this.refs.records.getDOMNode();
      const nodeOffsetTop = getOffsetTop(node);
      const calculatedHeight = window.innerHeight - nodeOffsetTop - 20;
      const minHeight = 335;
      this.setState({
        height: Math.max(calculatedHeight, minHeight)
      });
    }
  },

  handleFilter() {
    //e.preventDefault();
    const value = this.refs.filterInput.getDOMNode().value;
    this.props.answerEvents.onFilter.call(this, value);
  },
  render() {

    // use "destructuring" syntax to assign this.props.params.questionName to questionName
    const {
      answer,
      question,
      recordClass,
      displayInfo,
      filterTerm,
      filteredRecords,
      answerEvents,
      format
    } = this.props;

    const displayName = recordClass.get('displayName');
    const meta = answer.get('meta');
    const pagination = displayInfo.get('pagination');
    const firstRec = pagination.get('offset') + 1;
    const lastRec = Math.min(pagination.get('offset') + pagination.get('numRecords'),
                             meta.get('count'), filteredRecords.size);
    const Records = format === 'list' ? RecordList : RecordTable;

    return (
      <div>
        <h1>{question.get('displayName')}</h1>
          <div className="wdk-Answer">
            <div className="wdk-Answer-filter">
              <input
                ref="filterInput"
                className="wdk-Answer-filterInput"
                defaultValue={filterTerm}
                placeholder={`Filter ${displayName} records`}
                onKeyUp={throttle(this.handleFilter, 150, { leading: false })}
              />
              <i className="fa fa-search fa-lg wdk-Answer-filterIcon"/>
            </div>
            <p className="wdk-Answer-count">
              Showing {firstRec} - {lastRec} of {meta.get('count')} {displayName} records
            </p>
            <Records
              ref="records"
              height={this.state.height}
              meta={meta}
              records={filteredRecords}
              displayInfo={displayInfo}
              {...answerEvents}
            />
          </div>
      </div>
    );
  }

});

export default Answer;
