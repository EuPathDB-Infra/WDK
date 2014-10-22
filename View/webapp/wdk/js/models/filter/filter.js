wdk.namespace('wdk.models.filter', function(ns) {
  'use strict';

  /**
   * Creates a string description of the filter.
   * Provides filter methods based on type (string or number).
   */
  var Filter = Backbone.Model.extend({
    description: function() {
      return this.attributes.field + ' is ' + _.result(this, 'condition');
    },

    condition: function() {
      throw new Error('Filter.condition must be overridden by inheriting objects.');
    }
  });

  /**
   * Filters data by set membership of an attribute
   */
  var MemberFilter = Filter.extend({
    condition: function() {
      var values = this.get('values');

      if (values.length <= 2) {
        return values.join(' or ');
      } else {
        return values.slice(0, -1).join(', ') + ' or ' + values[values.length - 1];
      }
    }
  });

  /**
  * Filters data by range inclusion of an attribute
  */
  var RangeFilter = Filter.extend({
    condition: function() {
      var condition;
      var attrs = this.attributes;
      if (attrs.min !== null && attrs.max !== null) {
        condition = 'between ' + attrs.min + ' and ' + attrs.max;
      } else if (attrs.min !== null) {
        condition = 'at least ' + attrs.min;
      } else if (attrs.max !== null) {
        condition = 'at most ' + attrs.max;
      }
      return condition;
    }
  });

  var Filters = Backbone.Collection.extend({
    model: function(attrs, options) {
      if (attrs.operation === 'membership') {
        return new MemberFilter(attrs, options);
      } else if (attrs.operation === 'range') {
        return new RangeFilter(attrs, options);
      } else {
        throw new TypeError('Unkown operation: "' + attrs.operation + '". ' +
          'Supported operations are "membership" and "range"');
      }
    }
  });

  ns.Filter = Filter;
  ns.MemberFilter = MemberFilter;
  ns.RangeFilter = RangeFilter;
  ns.Filters = Filters;

});
