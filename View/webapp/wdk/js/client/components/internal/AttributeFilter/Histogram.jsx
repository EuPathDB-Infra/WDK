import $ from 'jquery';
import React from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import { debounce, isEqual, noop, throttle } from 'lodash';

import { lazy } from '../../../utils/componentUtils';
import DateSelector from '../../DateSelector';
import { formatDate } from './Utils';

var distributionEntryPropType = PropTypes.shape({
  value: PropTypes.number.isRequired,
  count: PropTypes.number.isRequired,
  filteredCount: PropTypes.number.isRequired
});

var Histogram = (function() {

  /** Common histogram component */
  class LazyHistogram extends React.Component {

    constructor(props) {
      super(props);
      this.handleResize = throttle(this.handleResize.bind(this), 100);
      // this.updatePlotScale = debounce(this.updatePlotScale, 100);
      this.emitStateChange = debounce(this.emitStateChange, 100);
      this.state = {
        uiState: this.getStateFromProps(props)
      };
    }

    componentDidMount() {
      $(window).on('resize', this.handleResize);
      $(ReactDOM.findDOMNode(this))
        .on('plotselected .chart', this.handlePlotSelected.bind(this))
        .on('plotselecting .chart', this.handlePlotSelecting.bind(this))
        .on('plotunselected .chart', this.handlePlotUnselected.bind(this))
        .on('plothover .chart', this.handlePlotHover.bind(this));

      this.createPlot();
      this.createTooltip();
      this.drawPlotSelection();
    }

    componentWillReceiveProps(nextProps) {
      if (nextProps.uiState !== this.state.uiState) {
        this.setState({ uiState: nextProps.uiState });
      }
    }

    /**
     * Conditionally update plot and selection based on props and state.
     */
    componentDidUpdate(prevProps) {
      if (!isEqual(this.props.distribution, prevProps.distribution)) {
        this.createPlot();
        this.drawPlotSelection();
      }

      if (
        prevProps.selectedMin !== this.props.selectedMin ||
        prevProps.selectedMax !== this.props.selectedMax
      ) {
        this.drawPlotSelection();
      }

      this.updatePlotScale(this.props.uiState);
    }

    componentWillUnmount() {
      $(window).off('resize', this.handleResize);
    }

    getStateFromProps(props) {
      // Set default yAxis max based on distribution
      var yaxisMax = this.computeYAxisMax(props);
      var values = props.distribution
        .map(entry => entry.value)
        .filter(value => value != null);

      var { xaxisMin, xaxisMax } = props.uiState;
      if (xaxisMin == null) xaxisMin = Math.min(...values);
      if (xaxisMax == null) xaxisMax = Math.max(...values);
      return { yaxisMax, xaxisMin, xaxisMax };
    }

    computeYAxisMax(props) {
      if (props.uiState.yaxisMax != null) return props.uiState.yaxisMax;

      var counts = props.distribution.map(entry => entry.count);
      // Reverse sort, then pull out first and second highest values
      var [ max, nextMax ] = counts.sort((a, b) => a < b ? 1 : -1);
      // If max is more than twice the size of nextMax, assume it is
      // an outlier and use nextMax as the max
      var yaxisMax = max >= nextMax * 2 ? nextMax : max;
      return yaxisMax + yaxisMax * 0.1;
    }

    handleResize() {
      this.plot.resize();
      this.plot.setupGrid();
      this.plot.draw();
      this.drawPlotSelection();
    }

    handlePlotSelected(event, ranges) {
      var range = unwrapXaxisRange(ranges);
      this.props.onSelected(range);
    }

    handlePlotSelecting(event, ranges) {
      if (!ranges) return;
      var range = unwrapXaxisRange(ranges);
      this.props.onSelecting(range);
    }

    handlePlotUnselected() {
      var range = { min: null, max: null };
      this.props.onSelected(range);
    }

    drawPlotSelection() {
      var values = this.props.distribution.map(entry => entry.value);
      var currentSelection = unwrapXaxisRange(this.plot.getSelection());
      var { selectedMin, selectedMax } = this.props;

      // Selection already matches current state
      if (selectedMin === currentSelection.min && selectedMax === currentSelection.max) {
        return;
      }

      if (selectedMin === null && selectedMax === null) {
        this.plot.clearSelection(true);
      } else {
        this.plot.setSelection({
          xaxis: {
            from: selectedMin === null ? Math.min(...values) : selectedMin,
            to: selectedMax === null ? Math.max(...values) : selectedMax
          }
        }, true);
      }
    }

    createPlot() {
      var { distribution, chartType, timeformat } = this.props;
      var { uiState } = this.state;

      var values = distribution.map(entry => entry.value);
      var min = Math.min(...values);
      var max = Math.max(...values);

      var barWidth = (max - min) * 0.005;

      var xaxisBaseOptions = chartType === 'date'
        ? { mode: 'time', timeformat: timeformat }
        : {};


      var seriesData = [{
        data: distribution.map(entry => [ entry.value, entry.count ]),
        color: '#AAAAAA'
      },{
        data: distribution.map(entry => [ entry.value, entry.filteredCount ]),
        color: '#DA7272',
        hoverable: false,
        // points: { show: true }
      }];

      var plotOptions = {
        series: {
          bars: {
            show: true,
            fillColor: { colors: [{ opacity: 1 }, { opacity: 1 }] },
            barWidth: barWidth,
            lineWidth: 0,
            align: 'center'
          }
        },
        xaxis: Object.assign({
          min: uiState.xaxisMin,
          max: uiState.xaxisMax,
          tickLength: 0
        }, xaxisBaseOptions),
        yaxis: {
          min: 0,
          max: uiState.yaxisMax
        },
        grid: {
          clickable: true,
          hoverable: true,
          autoHighlight: false,
          borderWidth: 0
        },
        selection: {
          mode: 'x',
          color: '#66A4E7'
        }
      };

      if (this.plot) this.plot.destroy();

      this.$chart = $(ReactDOM.findDOMNode(this)).find('.chart');
      this.plot = $.plot(this.$chart, seriesData, plotOptions);
    }

    createTooltip() {
      this.tooltip = this.$chart
        .qtip({
          prerender: true,
          content: ' ',
          position: {
            target: 'mouse',
            viewport: this.$el,
            my: 'bottom center'
          },
          show: false,
          hide: {
            event: false,
            fixed: true
          },
          style: {
            classes: 'qtip-tipsy'
          }
        });
    }

    handlePlotHover(event, pos, item) {
      var qtipApi = this.tooltip.qtip('api'),
        previousPoint;

      if (!item) {
        qtipApi.cache.point = false;
        return qtipApi.hide(item);
      }

      previousPoint = qtipApi.cache.point;

      if (previousPoint !== item.dataIndex) {
        qtipApi.cache.point = item.dataIndex;
        var entry = this.props.distribution[item.dataIndex];
        var formattedValue = this.props.chartType === 'date'
          ? formatDate(this.props.timeformat, entry.value)
          : entry.value;

        // FIXME Format date
        qtipApi.set('content.text',
          this.props.xaxisLabel + ': ' + formattedValue +
          '<br/>All ' + this.props.yaxisLabel + ': ' + entry.count +
          '<br/>Matching ' + this.props.yaxisLabel + ': ' + entry.filteredCount);
        qtipApi.elements.tooltip.stop(1, 1);
        qtipApi.show(item);
      }
    }

    updatePlotScale(partialUiState) {
      const { yaxisMax, xaxisMin, xaxisMax } = partialUiState;

      if (yaxisMax == null && xaxisMin == null && xaxisMax == null) return;

      const plotOptions = this.plot.getOptions();

      const {
        yaxes: { 0: { max: currYaxisMax } },
        xaxes: { 0: { min: currXaxisMin, max: currXaxisMax } }
      } = plotOptions;

      if (
        (yaxisMax != null && yaxisMax !== currYaxisMax) ||
        (xaxisMin != null && xaxisMin !== currXaxisMin) ||
        (xaxisMax != null && xaxisMax !== currXaxisMax)
      ) {
        if (yaxisMax != null) plotOptions.yaxes[0].max = yaxisMax;
        if (xaxisMin != null) plotOptions.xaxes[0].min = xaxisMin;
        if (xaxisMax != null) plotOptions.xaxes[0].max = xaxisMax;
        this.plot.setupGrid();
        this.plot.draw();
      }
    }

    updateUIState(uiState) {
      this.setState({ uiState });
      this.emitStateChange(uiState);
    }

    emitStateChange(uiState) {
      this.props.onUiStateChange(uiState);
    }

    setYAxisMax(yaxisMax) {
      this.updateUIState(Object.assign({}, this.state.uiState, { yaxisMax }));
    }

    setXAxisScale(xaxisMin, xaxisMax) {
      this.updateUIState(Object.assign({}, this.state.uiState, { xaxisMin, xaxisMax }));
    }

    render() {
      var { xaxisLabel, yaxisLabel, chartType, timeformat, distribution } = this.props;
      var { yaxisMax, xaxisMin, xaxisMax } = this.state.uiState;

      var counts = distribution.map(entry => entry.count);
      var countsMin = Math.min(...counts);
      var countsMax = Math.max(...counts);

      var values = distribution.map(entry => entry.value).filter(value => value != null);
      var valuesMin = Math.min(...values);
      var valuesMax = Math.max(...values);

      var xaxisMinSelector = chartType === 'date' ? (
        <DateSelector
          value={formatDate(timeformat, xaxisMin)}
          start={formatDate(timeformat, valuesMin)}
          end={formatDate(timeformat, xaxisMax)}
          onChange={value => this.setXAxisScale(new Date(value).getTime(), xaxisMax)}
        />
      ) : (
        <input
          type="number"
          value={xaxisMin}
          min={valuesMin}
          max={xaxisMax}
          onChange={e => this.setXAxisScale(Number(e.target.value), xaxisMax)}
        />
      );

      var xaxisMaxSelector = chartType === 'date' ? (
        <DateSelector
          value={formatDate(timeformat, xaxisMax)}
          start={formatDate(timeformat, xaxisMin)}
          end={formatDate(timeformat, valuesMax)}
          onChange={value => this.setXAxisScale(xaxisMin, new Date(value).getTime())}
        />
      ) : (
        <input
          type="number"
          value={xaxisMax}
          min={xaxisMin}
          max={valuesMax}
          onChange={e => this.setXAxisScale(xaxisMin, Number(e.target.value))}
        />
      );

      return (
        <div className="chart-container">
          <div className="chart"></div>
          <div className="chart-title x-axis">{xaxisLabel}</div>
          <div>
            Display {xaxisLabel} between {xaxisMinSelector} and {xaxisMaxSelector} <button
              type="button"
              onClick={() => this.setXAxisScale(valuesMin, valuesMax)}
            >reset</button>
          </div>
          <div className="chart-title y-axis">
            <div>{yaxisLabel}</div>
            <div>
              <input
                style={{width: '90%'}}
                type="range"
                min={Math.max(countsMin, 1)}
                max={countsMax + countsMax * 0.1}
                title={yaxisMax}
                value={yaxisMax}
                onChange={e => this.setYAxisMax(Number(e.target.value))}/>
            </div>
          </div>
        </div>
      );
    }

  }

  LazyHistogram.propTypes = {
    distribution: PropTypes.arrayOf(distributionEntryPropType).isRequired,
    selectedMin: PropTypes.number,
    selectedMax: PropTypes.number,
    chartType: PropTypes.oneOf([ 'number', 'date' ]).isRequired,
    timeformat: PropTypes.string,
    xaxisLabel: PropTypes.string,
    yaxisLabel: PropTypes.string,

    uiState: PropTypes.shape({
      xaxisMin: PropTypes.number,
      xaxisMax: PropTypes.number,
      yaxixMax: PropTypes.number
    }),

    onUiStateChange: PropTypes.func,

    onSelected: PropTypes.func,
    onSelecting: PropTypes.func,
    onUnselected: PropTypes.func
  };

  LazyHistogram.defaultProps = {
    xaxisLabel: 'X-Axis',
    yaxisLabel: 'Y-Axis',
    selectedMin: null,
    selectedMax: null,
    uiState: {},
    onSelected: noop,
    onSelecting: noop,
    onUnselected: noop
  };

  return lazy(function(render) {
    require(
      [
        'lib/jquery-flot',
        'lib/jquery-flot-categories',
        'lib/jquery-flot-selection',
        'lib/jquery-flot-time'
      ],
      render)
  })(LazyHistogram);
})();

export default Histogram;

/**
 * Reusable histogram field component. The parent component is responsible for
 * preparing the data.
 */
function unwrapXaxisRange(flotRanges) {
  if (flotRanges == null) {
    return { min: null, max: null };
  }

  var { from, to } = flotRanges.xaxis;
  var min = Number(from.toFixed(2));
  var max = Number(to.toFixed(2));
  return { min, max };
}
