wdk.namespace('wdk.views.filter', function(ns, $) {
  'use strict';

  // Renders a list of links
  //
  // When a link is clicked, a 'select' event is triggered by the view.
  // Event handlers will recieve the field model as an argument.

  ns.FieldListView = wdk.views.View.extend({

    events: {
      'click a[href="#expand"]'   : 'expand',
      'click a[href="#collapse"]' : 'collapse',
      'click li a'                : 'triggerSelect',
      'click h4'                  : 'toggleNext',
      'keyup input'               : 'filter'
    },

    template: wdk.templates['filter/field_list.handlebars'],

    initialize: function(options) {
      this.trimMetadataTerms = options.trimMetadataTerms;

      this.listenTo(this.controller, 'select:field', this.selectField);
      this.listenTo(this.controller.fields, 'reset', this.render);
    },

    render: function() {
      var groupedFields = this.controller.fields.getTree({
        trimMetadataTerms: this.trimMetadataTerms
      });

      this.$el.html(this.template({
        nodes: groupedFields,
        showExpand: groupedFields.filter(function(node) {
          return !_.isEmpty(node.children);
        }).length
      }));

      return this;
    },

    triggerSelect: function(e) {
      e.preventDefault();

      var link = e.currentTarget;
      if ($(link).parent().hasClass('active')) {
        return;
      }

      var term = link.hash.slice(1);
      var field = this.controller.fields.findWhere({term: term});
      this.controller.selectField(field);
    },

    expand: wdk.fn.preventEvent(function() {
      this.$('h4').removeClass('collapsed');
    }),

    collapse: wdk.fn.preventEvent(function() {
      this.$('h4').addClass('collapsed');
    }),

    toggleNext: function(e) {
      var $target = $(e.currentTarget);
      $target.toggleClass('collapsed');
    },

    // jshint ignore:start
    filter: function(e) {
      // var str = e.currentTarget.value;
      // this.$('div')
      // .hide()
      // .find(':contains(' + str + ')').show();
    },
    // jshint ignore:end

    selectField: function(field) {
      var term = field.get('term');
      var link = this.$('a[href="#' + term + '"]');
      this.$('li').removeClass('active');
      $(link).parent().addClass('active');
      $(link).parentsUntil(this.$el.find('>ul')).find('>h4').removeClass('collapsed');
    }

  });

});
