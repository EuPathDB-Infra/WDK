wdk.namespace('wdk.views.filter', function(ns) {
  'use strict';

  var notNaN = function(n) { return !_.isNaN(n) };

  ns.RangeFilterView = wdk.views.View.extend({

    plot: null,

    min: null,

    max: null,

    events: {
      'click .read-more a'    : 'expandDescription',
      'plothover .chart'      : 'handlePlotHover',
      'plotselected .chart'   : 'handlePlotSelected',
      'plotselecting .chart'  : 'handlePlotSelecting',
      'plotunselected .chart' : 'handlePlotUnselected',
      'keyup input'           : _.debounce(function(e) {
        this.handleFormChange(e);
      }, 300)
    },

    template: wdk.templates['filter/range_filter.handlebars'],

    constructor: function(filterService) {
      var initArgs = [].slice.call(arguments, 1);
      this.filterService = filterService;
      wdk.views.View.apply(this, initArgs);
    },

    initialize: function() {
      var filters = this.filterService.filters;
      this.listenTo(filters, 'add', this.addFilter);
      this.listenTo(filters, 'remove', this.removeFilter);
    },

    addFilter: function(filter, filters, options) {
      var update = options.origin !== this &&
        filter.get('field') === this.model.get('term');

      if (update) {
        this.$min.val(filter.get('min'));
        this.$max.val(filter.get('max'));
        this.doPlotSelection();
      }
    },

    removeFilter: function(filter, filters, options) {
      var update = options.origin !== this &&
        filter.get('field') === this.model.get('term');

      if (update) {
        this.$min.val(null);
        this.$max.val(null);
        this.doPlotSelection();
      }
    },

    render: function() {
      var field = this.model;
      var filterService = this.filterService;
      var filter = filterService.filters.findWhere({
        field: field.get('term')
      });
      var filterValues = filter ? filter.pick('min', 'max') : null;

      var values = field.get('values').filter(notNaN);

      var distribution = _(values).countBy();
      var xdata = _(distribution).keys().map(Number);
      var ydata = _(distribution).values().map(Number);

      var fdistribution = _(field.get('filteredValues').filter(notNaN)).countBy();
      var xfdata = _(fdistribution).keys().map(Number);
      var yfdata = _(fdistribution).values().map(Number);

      var min = this.min = _.min(values);
      var max = this.max = _.max(values);
      var sum = _(values).reduce(function(sum, num) {
        return sum + num;
      }, 0);
      var avg = (sum / _.size(values)).toFixed(2);

      // [ [x1, y1], [x2, y2], ... ]
      var seriesData = [{
        data: _.zip(xdata, ydata),
        color: '#000'
      },{
        data: _.zip(xfdata, yfdata),
        color: 'red',
        hoverable: false
      }];

      var plotOptions = {
        series: {
          bars: {
            show: true,
            barWidth: 0.5,
            lineWidth: 0,
            align: 'center'
          }
        },
        xaxis: {
          //mode: 'categories',
          min: min - 10,
          max: max + 10,
          tickLength: 0
        },
        grid: {
          clickable: true,
          hoverable: true,
          autoHighlight: false,
          borderWidth: 0
        },
        selection: {
          mode: 'x',
          color: '#66A4E7' // was '#2a6496'
        }
      };

      this.$el.html(this.template({
        field: field.attributes,
        distribution: distribution,
        min: min,
        max: max,
        avg: avg
      }));

      this.plot = wdk.$.plot(this.$('.chart'), seriesData, plotOptions);
      this.$min = this.$('input[name="min"]');
      this.$max = this.$('input[name="max"]');
      this.setSelectionTotal(filter);

      if (filterValues) {
        this.$min.val(filterValues.min);
        this.$max.val(filterValues.max);
        this.plot.setSelection({
          xaxis: {
            from: filterValues.min,
            to: filterValues.max
          }
        }, true);
      }

      $(window).on('resize.range_filter', this.resizePlot.bind(this));

      // activate Read more link if text is overflowed
      var p = this.$('.description p').get(0);
      if (p && p.scrollWidth > p.clientWidth) {
        this.$('.description .read-more').addClass('visible');
      }

      return this;
    },

    expandDescription: function(event) {
      event.preventDefault();
      this.$('.description p').toggleClass('expanded');
    },

    handlePlotClick: function(event, pos, item) {
      if (item) {
        if (item.highlighted) {
          this.plot.unhighlight(item.series, item.datapoint);
          item.highlighted = false;
        } else {
          this.plot.highlight(item.series, item.datapoint);
          item.highlighted = true;
        }
      }
    },

    handlePlotHover: function(event, pos, item) {
      var tooltip = this.$('.chart-tooltip');
      var offset = this.$el.offset();
      if (item) {
        var x = item.datapoint[0];
        var y = item.datapoint[1];
        tooltip
          .css({
            display:'inline-block',
            top: item.pageY - offset.top + 5,
            left: item.pageX - offset.left + 5
          })
          .html('<strong>' + this.model.get('display') + '</strong> ' + x +
                '<br><strong>Frequency</strong> ' + y);
      } else {
        tooltip.css('display', 'none');
      }
    },

    handlePlotSelecting: function(event, ranges) {
      if (!ranges) return;

      var min = ranges.xaxis.from.toFixed(2);
      var max = ranges.xaxis.to.toFixed(2);

      this.$min.val(min);
      this.$max.val(max);
      this.setSelectionTotal(null);
    },

    handlePlotSelected: function(event, ranges) {
      var min = Number(ranges.xaxis.from.toFixed(2));
      var max = Number(ranges.xaxis.to.toFixed(2));
      var filters = this.filterService.filters;
      var field = this.model;

      filters.remove(filters.where({ field: field.get('term') }), { origin: this });

      var filter = filters.add({
        field: field.get('term'),
        operation: field.get('filter'),
        min: min,
        max: max
      }, { origin: this });

      this.setSelectionTotal(filter);
    },

    handlePlotUnselected: function(event) {
      var filters = this.filterService.filters;
      var field = this.model;

      this.$min.val(null);
      this.$max.val(null);

      this.setSelectionTotal(null);

      filters.remove(filters.where({ field: field.get('term') }), { origin: this });
    },

    handleFormChange: function(e) {
      var min = this.$min.val() === '' ? null : this.$min.val();
      var max = this.$max.val() === '' ? null : this.$max.val();

      var filters = this.filterService.filters;
      var field = this.model;

      // bail if values aren't changed
      var oldVals = _.result(this.plot.getSelection(), 'xaxis');
      if (oldVals && min === oldVals.from.toFixed(2) && max === oldVals.to.toFixed(2)) return;

      filters.remove(filters.where({ field: field.get('term') }), { origin: this });

      if (min === null && max === null) {
        this.setSelectionTotal(null);
      } else {
        var filter = filters.add({
          field: field.get('term'),
          operation: field.get('filter'),
          min: min,
          max: max
        }, { origin: this });
        this.setSelectionTotal(filter);
      }
      this.doPlotSelection();
    },

    doPlotSelection: function() {
      var min = this.$min.val() === '' ? null : this.$min.val();
      var max = this.$max.val() === '' ? null : this.$max.val();
      if (min === null && max === null) {
        this.plot.clearSelection(true);
      } else {
        this.plot.setSelection({
          xaxis: {
            from: min === null ? this.min : min,
            to: max === null ? this.max : max
          }
        }, true);
      }
    },

    setSelectionTotal: function(filter) {
      if (filter) {
        var selectionTotal = this.filterService.applyRangeFilter(filter,
          this.filterService.data).length;
        this.$('.selection-total').html('(' + selectionTotal + ' items selected)');
      } else {
        this.$('.selection-total').empty();
      }
    },

    resizePlot: _.debounce(function resizePlot() {
      this.plot.resize();
      this.plot.setupGrid();
      this.plot.draw();
      this.doPlotSelection();
    }, 300),

    didShow: function() {
      this.resizePlot();
      $(window).on('resize.range_filter', this.resizePlot.bind(this));
    },

    didHide: function() {
      $(window).off('resize.range_filter');
    },

    undelegateEvents: function() {
      $(window).off('.range_filter');
      wdk.views.View.prototype.undelegateEvents.apply(this, arguments);
    }

  });

});
