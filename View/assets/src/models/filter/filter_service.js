wdk.namespace('wdk.models.filter', function(ns) {
  'use strict';

  var MemberFilter = wdk.models.filter.MemberFilter;
  var RangeFilter = wdk.models.filter.RangeFilter;

  var Filters = wdk.models.filter.Filters;
  var Fields = wdk.models.filter.Fields;
  var Field = wdk.models.filter.Field;

  var Datum = Backbone.Model.extend({
    idAttribute: 'term',
    defaults: {
      ignored: false
    }
  });

  var Data = Backbone.Collection.extend({
    model: Datum
  });

  // FIXME Treat metadata, metadataSpec, and values as separate
  // entities and operate in such a way.
  //
  // Thus, the data object below will contain those three
  // objects: { values, metadata, metadataSpec }
  /**
   * Base class for FilterService classes.
   *
   * A subclass should only need to implement an
   * `applyFilters` method. This method's job is to apply
   * added filters to the data. This may be done locally
   * using JavaScript functions, or it may use a remote
   * service to retrieve filtered results.
   *
   */
  var FilterService = Backbone.Model.extend({

    /**
     * Collection of unfiltered data
     */
    data: null,

    /**
     * Collection of filterable fields
     */
    fields: null,

    /**
     * Collection of data with filters applied
     */
    filteredData: null,

    /**
     * Collection of applied Filters
     */
    filters: null,

    constructor: function() {
      this.data = new Data();
      this.filteredData = new Data();
      this.filters = new Filters();
      this.fields = new Fields();

      Backbone.Model.apply(this, arguments);
    },

    parse: function(attrs) {
      console.time('set data');
      this.data.reset(attrs.data);
      console.timeEnd('set data');
      console.time('set fields');
      this.fields.reset(attrs.fields);
      console.timeEnd('set fields');
      this.filters.reset(attrs.filters);
      return { title: attrs.title || 'Items' };
    },

    initialize: function() {
      this.filterChangeSet = [];
      this.listenTo(this.filters, 'add remove reset', function(m) { this.filterChangeSet.push(m); });
      this.listenTo(this.filters, 'add remove reset', _.debounce(this.applyFilters, 100));
      this.applyFilters();
    },

    reset: function(attrs) {
      this.parse(attrs);
      this.applyFilters();
    },

    /**
     * Pluck out values from data for each field and set to values attribute
     */
    setFieldValues: function() {
      var fs = this;
      this.fields.forEach(function(field) {
        console.time('set field :: ' + field.get('term'));
        var values = _(fs.data.pluck('metadata')).pluck(field.get('term'));
        field.set('values', values);
        console.timeEnd('set field :: ' + field.get('term'));
      });
    },

    getFieldValues: function(field) {
      var term = field.get('term');
      return _.pluck(this.data.pluck('metadata'), term)
        .map(function(v){ return v === undefined ? Field.UNKNOWN_VALUE : v; });
    },

    getFieldFilteredValues: function(field) {
      var term = field.get('term');

      return this.getFilteredData({ omit: [term] })
        .map(function(d) {
          var value = d.get('metadata')[term];
          return _.isUndefined(value) ? Field.UNKNOWN_VALUE : value;
        });
    },

    /**
     * Pluck out values from filtered data for each field and set to values
     * attribute, omitting filters applied to the field. This is useful for
     * visualizing the distribution of other fiters with respect to the field.
     */
    setFieldFilteredValues: function() {
      var fs = this;
      this.fields.forEach(function(field) {
        var term = field.get('term');
        var values = fs.getFilteredData({ omit: [term] })
          .map(function(d) { return d.get('metadata')[term]; });
        field.set('filteredValues', values);
      });
    },

    /**
     * Call getFilteredData and use result to reset filteredData
     *
     * TODO Add filterChangeSet to options
     */
    applyFilters: function() {
      var data = this.getFilteredData();
      this.filteredData.reset(data, {
        filterChangeSet: this.filterChangeSet
      });
      this.filterChangeSet = [];
      return this;
    },

    /**
     * Returns a subset of data with filters applied
     *
     * Implementing subclasses should override this method.
     */
    getFilteredData: function() { return this; }

  });

  var LocalFilterService = FilterService.extend({

    /**
     * Applies filters and returns the raw data
     *
     * Optionally, provide options to affect the filtering algorithm.
     * Available options are:
     *   * omit: A list of fields to omit. This is useful when you want
     *           a distribution of values caused by other filters.
     *
     * @param {Object} options Options for the filtering algorithm
     */
    getFilteredData: function(options) {
      var service = this;
      var filters = this.filters;
      var data;

      if (options && options.omit) {
        filters = filters.reject(function(filter) {
          return _.contains(options.omit, filter.get('field'));
        });
      }

      if (filters.length) {
        data = filters.reduce(function(data, filter) {
          if (filter instanceof MemberFilter) {
            return service.applyMemberFilter(filter, data);
          } else if (filter instanceof RangeFilter) {
            return service.applyRangeFilter(filter, data);
          }
        }, service.data);
      }
      return data || [];
    },

    applyMemberFilter: function(filter, data) {
      var field = filter.get('field');
      var values = filter.get('values');

      console.time('for-loop :: ' + field + ' - ' + values);
      var d = data.filter(function(d) {
        var value = d.get('metadata')[field] || Field.UNKNOWN_VALUE;
        var index = values.length;

        // Use a for loop for efficiency
        while(index--) {
          if (values[index] === value) break;
        }

        return index > -1;
      });
      console.timeEnd('for-loop :: ' + field + ' - ' + values);
      return d;
    },

    applyRangeFilter: function(filter, data) {
      var field = filter.get('field');
      var min = filter.get('min');
      var max = filter.get('max');
      var test;

      if (min !== null && max !== null) {
        test = function(d) {
          var v = d.get('metadata')[field];
          return v >= min && v <= max;
        };
      } else if (min !== null) {
        test = function(d) {
          return d.get('metadata')[field] >= min;
        };
      } else if (max !== null) {
        test = function(d) {
          return d.get('metadata')[field] <= max;
        };
      }

      return data.filter(test);
    }

  });

  ns.FilterService = FilterService;
  ns.LocalFilterService = LocalFilterService;

});
