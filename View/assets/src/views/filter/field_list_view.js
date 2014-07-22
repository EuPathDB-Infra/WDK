wdk.namespace('wdk.views.filter', function(ns, $) {
  'use strict';

  /**
   * Renders a list of links
   *
   * When a link is clicked, a 'select' event is triggered by the view.
   * Event handlers will recieve the field model as an argument.
   */
  ns.FieldListView = wdk.views.View.extend({

    events: {
      'click a[href="#expand"]': 'expand',
      'click a[href="#collapse"]': 'collapse',
      'click li a': 'triggerSelect',
      'click h4': 'toggleNext',
      'keyup input': 'filter'
    },

    template: wdk.templates['filter/field_list.handlebars'],

    initialize: function() {
      this.listenTo(this.model.fields, 'select', this.selectField);
      this.listenTo(this.model.fields, 'reset', this.render);
    },

    render: function() {
      var prunedFields = _.sortBy(function getFields(fields) {
        return _.where(fields, { filterable: true })
          .reduce(function(acc, field) {
            while (field.parent) {
              acc.push(field);
              field = _.findWhere(fields, {term: field.parent});
            }
            acc.push(field);
            return _.uniq(acc);
          }, []);
      }(this.model.fields.toJSON()), 'term');

      var rootNodes = _.where(prunedFields, { parent: undefined });

      var groupedFields = (function appendChildren(nodes, fields) {
        return _.map(nodes, function(node) {
          var children = _.chain(fields)
            .where({ parent: node.term })
            .sortBy(function(c) { return c.filterable ? 0 : 1; }) // push leaves above nodes
            .value();

          return children.length
            ? { field: node, children: appendChildren(children, fields) }
            : { field: node };
        });
      }(rootNodes, prunedFields));

      this.$el.html(this.template(groupedFields));
      return this;
    },

    triggerSelect: function(e) {
      e.preventDefault();

      var link = e.currentTarget;
      if ($(link).parent().hasClass('active')) {
        return;
      }

      var term = link.hash.slice(1);
      var field = this.model.fields.findWhere({term: term});
      field.select();
    },

    expand: wdk.fn.preventEvent(function() {
      this.$('h4').removeClass('collapsed');
      // this.$('div').slideDown(function() {
      //   $(this).prev().removeClass('collapsed');
      // });
    }),

    collapse: wdk.fn.preventEvent(function() {
      this.$('h4').addClass('collapsed');
      // this.$('div').slideUp(function() {
      //   $(this).prev().addClass('collapsed');
      // });
    }),

    toggleNext: function(e) {
      var $target = $(e.currentTarget);
      $target.toggleClass('collapsed');
      // var $next = $target.next().slideToggle(function() {
      //   $target.toggleClass('collapsed', $next.is(':hidden'));
      // });
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
    }

  });

});
