import { debounce, flow, get, isEqual, partial } from 'lodash';
import React from 'react';
import ReactDOM from 'react-dom';

import {
  ActiveQuestionUpdatedAction,
  ParamValueUpdatedAction,
  QuestionErrorAction
} from '../actioncreators/QuestionActionCreators';
import * as ParamModules from '../params';
import QuestionStore, { State } from '../stores/QuestionStore';
import { Seq } from '../utils/IterableUtils';
import { Parameter } from '../utils/WdkModel';
import AbstractViewController from './AbstractViewController';
import { Context } from '../params/Utils';

export const UNRECOVERABLE_PARAM_ERROR_EVENT = 'unrecoverable-param-error';

const ActionCreators = {
  setActiveQuestion: ActiveQuestionUpdatedAction.create,
  updateParamValue: ParamValueUpdatedAction.create
}

type Props = {
  questionName: string;
  paramName: string;
  paramValues: Record<string, string>;
  stepId: number | undefined;
}

type QuestionState = State['questions'][string];

export default class LegacyParamController extends AbstractViewController<
  QuestionState,
  QuestionStore,
  typeof ActionCreators,
  Props
> {

  paramModules = ParamModules;

  getStoreClass() {
    return QuestionStore;
  }

  getStateFromStore() {
    return get(this.store.getState(), ['questions', this.props.questionName], {}) as QuestionState;
  }

  getActionCreators() {
    return ActionCreators;
  }

  getDependentParams(parameter: Parameter): Seq<Parameter> {
    return Seq.from(parameter.dependentParams)
      .map(name => this.state.question.parametersByName[name])
      .flatMap(dependentParam =>
        Seq.of(dependentParam).concat(this.getDependentParams(dependentParam)));
  }

  loadData(prevProps?: Props, prevState?: QuestionState) {
    if (
      this.state.questionStatus == null ||
      this.state.stepId !== this.props.stepId
    ) {
      this.eventHandlers.setActiveQuestion({
        questionName: this.props.questionName,
        paramValues: this.props.paramValues,
        stepId: this.props.stepId
      });
    }

    else if (prevProps != null) {
      let prevParamValues = prevProps.paramValues || {};
      let paramValues = this.props.paramValues || {};
      let changedParams = Object.entries(paramValues)
        .filter(([name, value]) => (
          prevParamValues[name] !== value &&
          this.state.paramValues[name] !== value
        ));
      if (changedParams.length > 1) {
        console.warn('Received multiple changed param values: %o', changedParams);
      }
      changedParams.forEach(([name, paramValue]) => {
        let parameter = this.state.question.parameters.find(p => p.name === name);
        if (parameter) {
          const dependentParameters = this.getDependentParams(parameter).toArray();
          this.eventHandlers.updateParamValue({
            ...this.getContext(parameter),
            paramValue,
            dependentParameters
          });
        }
      });
    }

    // Trigger event in case of question error
    if (
      get(prevState, 'questionStatus') !== get(this.state, 'questionStatus') &&
      this.state.questionStatus === 'error' &&
      this.props.paramValues != null
    ) {
      const node = ReactDOM.findDOMNode(this);
      const event = new Event(UNRECOVERABLE_PARAM_ERROR_EVENT, { bubbles: true, cancelable: false });
      node.dispatchEvent(event);
    }
  }

  isRenderDataLoadError() {
    return this.state.questionStatus === 'error';
  }

  isRenderDataLoaded() {
    return this.state.questionStatus === 'complete';
  }

  isRenderDataNotFound() {
    return this.state.questionStatus === 'not-found';
  }

  getContext<T extends Parameter>(parameter: T): Context<T> {
    return {
      questionName: this.state.question.urlSegment,
      parameter: parameter,
      paramValues: this.state.paramValues
    }
  }

  renderDataLoadError() {
    const isProbablyRevise = this.props.paramValues != null;
    const errorMessage = 'Data for this parameter could not be loaded.' +
      (isProbablyRevise ? ' The strategy this search belongs to will have to recreated.' : '');

    return (
      <div>
        <div style={{ color: 'red', fontSize: '1.4em', fontWeight: 500 }}>
          {errorMessage}
        </div>

        {isProbablyRevise && [
          <div style={{ fontWeight: 'bold', padding: '1em 0' }}>Current value:</div>,
          <div style={{ maxHeight: 300, overflow: 'auto', background: '#f3f3f3' }}>
            <pre>{prettyPrintRawValue(this.props.paramValues[this.props.paramName])}</pre>
          </div>
        ]}
      </div>
    );
  }

  renderView() {
    const parameter = this.state.question.parameters.find(p => p.name === this.props.paramName);

    if (parameter == null) return null;

    const ctx = this.getContext(parameter);

    if (this.state.paramErrors[parameter.name]) {
      return (
        <div>
          <div style={{ color: 'red', fontSize: '2em', fontStyle: 'italic', margin: '1em 0' }}>
            Oops... something went wrong.
          </div>
          <p>Not all of the data could be loaded. Support staff have been notified of the problem and are looking into it.</p>
        </div>
      )
    }

    return (
      <div>
        <this.paramModules.ParamComponent
          ctx={ctx}
          parameter={parameter}
          dispatch={this.dispatchAction}
          value={this.state.paramValues[parameter.name]}
          uiState={this.state.paramUIState[parameter.name]}
          onParamValueChange={(paramValue: string) => {
            const dependentParameters = this.getDependentParams(parameter).toArray();
            this.eventHandlers.updateParamValue({
              ...ctx,
              paramValue,
              dependentParameters
            });
          }}
        />
        <ParamterInput
          name={this.props.paramName}
          value={this.state.paramValues[this.props.paramName]}
        />
      </div>
    )
  }

}

type ParameterInputProps = {
  name: string;
  value: string;
}

/**
 * Input element that emits change events so that it can participate in classic
 * question page (see wdk/js/components/paramterHandlers.js).
 */
class ParamterInput extends React.Component<ParameterInputProps> {

  input: HTMLInputElement | null;

  dispatchChangeEvent = debounce(this._dispatchChangeEvent, 1000);

  componentDidUpdate(prevProps: ParameterInputProps) {
    if (prevProps.value !== this.props.value) {
      this.dispatchChangeEvent();
    }
  }

  _dispatchChangeEvent() {
    if (this.input == null) {
      console.warn("Input field is not defined. Skipping event dispatch.");
      return;
    }
    this.input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  render() {
    return (
      <input
        ref={el => this.input = el}
        type="hidden"
        id={this.props.name}
        name={`value(${this.props.name})`}
        value={this.props.value}
      />
    );
  }

}

function prettyPrintRawValue(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 4);
  }
  catch (e) {
    return value;
  }
}
