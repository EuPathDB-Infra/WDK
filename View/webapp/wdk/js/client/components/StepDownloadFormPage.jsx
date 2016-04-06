import React from 'react';
import { wrappable, filterOutProps } from '../utils/componentUtils';
import StepDownloadForm from './StepDownloadForm';

let NO_REPORTER_SELECTED = "_none_";

let ReporterSelect = props => {
  let { reporters, selected, onChange } = props;
  if (reporters.length == 0) return ( <noscript/> );
  return (
    <div>
      <span style={{marginRight:'0.5em'}}>Choose a Reporter:</span>
      <select value={selected} onChange={onChange}>
        <option value={NO_REPORTER_SELECTED}>Please Select...</option>
        {reporters.map(reporter =>
          ( <option key={reporter.name} value={reporter.name}>{reporter.displayName}</option> ))}
      </select>
    </div>
  );
}

let StepDownloadFormPage = React.createClass({

  changeReporter(event) {
    // convert "none" back to null value
    let newValue = event.target.value;
    if (newValue === NO_REPORTER_SELECTED) {
      newValue = null;
    }
    this.props.onReporterChange(newValue);
  },

  render() {

    // get the props needed in this component's render
    let { availableReporters, selectedReporter, recordClass, onSubmit } = this.props;

    // filter props we don't want to send to the child form
    let formProps = filterOutProps(this.props, [ 'onReporterChange' ]);

    // incoming store value of null indicates no format currently selected
    if (selectedReporter == null) {
      selectedReporter = NO_REPORTER_SELECTED;
    }

    return (
      <div style={{margin: '1em 3em'}}>
        <h1>Download Step Result: {this.props.step.displayName}</h1>
        <ReporterSelect reporters={availableReporters} selected={selectedReporter} onChange={this.changeReporter}/>
        <StepDownloadForm {...formProps}/>
      </div>
    );
  }

});

export default wrappable(StepDownloadFormPage);
