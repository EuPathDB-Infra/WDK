wdk.namespace('wdk.views.filter', function(ns) {
  'use strict';

  var FieldListView = ns.FieldListView;
  var FieldDetailView = ns.FieldDetailView;

  /**
   * A FilterView is composed of several components:
   * - A selectable list of available filterable fields.
   * - A detail view for a specific field.
   *
   */
  ns.FilterFieldsView = wdk.views.View.extend({

    template: wdk.templates['filter/filter_fields.handlebars'],

    className: 'filters ui-helper-clearfix',

    initialize: function(options) {
      this.fieldList = new FieldListView({ model: this.model, trimMetadataTerms: options.trimMetadataTerms });
      this.fieldDetail = new FieldDetailView({ model: this.model });

      this.listenTo(this.model.fields, 'select', this.renderDetail);

      this.render();
    },

    render: function() {
      this.$el.html(this.template(this.model.filteredData));
      this.fieldList.setElement(this.$('.field-list')).render();
      this.fieldDetail.setElement(this.$('.field-detail')).render();
    },

    renderDetail: function(field) {
      this.fieldDetail.render(field);
    },

    didShow: function() {
      this.fieldDetail.show();
    },

    // FIXME This logic should be in the Field model
    setFilter: function(field, filterValues) {
      var filters = this.model.filters;

      // remove previous filters for this field
      filters.remove(filters.where({ field: field.get('term') }));

      if (filterValues) {
        var filter = _.extend({
          field: field.get('term'),
          operation: field.get('filter')
        }, filterValues);

        filters.add(filter);
      }
    }

  });

});
