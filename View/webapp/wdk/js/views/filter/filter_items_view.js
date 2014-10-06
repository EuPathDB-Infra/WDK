wdk.namespace('wdk.views.filter', function(ns) {
  'use strict';

  var FilterItemView = ns.FilterItemView;

  ns.FilterItemsView = wdk.views.View.extend({
    itemViews: null,

    className: 'filter-items',

    tagName: 'ul',

    constructor: function(filterService) {
      var restArgs = [].slice.call(arguments, 1);
      this.filterService = filterService;
      wdk.views.View.apply(this, restArgs);
    },

    initialize: function() {
      this.itemViews = {};
      this.listenTo(this.model, 'add', this.addItem);
      this.listenTo(this.model, 'remove', this.removeItem);
      this.listenTo(this.model, 'reset', this.render);
      this.listenTo(this.controller, 'select:field', this.toggleSelectItems);
      this.listenTo(this.controller, 'change:selectedData', this.updateTotal);
      this.render();
    },

    render: function() {
      var _this = this;

      // remove existing items
      _.values(this.itemViews).forEach(function(view) {
        _this.removeItem(view.model);
      });

      // add new items
      this.model.forEach(function(model) {
        _this.addItem(model, { inRender: true });
      });

      this.updateTotal();

      return this;
    },

    addItem: function(model, options) {
      var itemView = new FilterItemView(this.filterService, {
        model: model,
        controller: this.controller
      });
      this.$el.append(itemView.$el);
      this.itemViews[model.cid] = itemView;
      if (!options.inRender) {
        itemView.select();
      }
    },

    removeItem: function(model) {
      this.itemViews[model.cid].remove();
      delete this.itemViews[model.cid];
    },

    toggleSelectItems: function(field) {
      _.invoke(this.itemViews, 'toggleSelect', field);
    },

    updateTotal: function() {
      var data = this.controller.getSelectedData();
      var count = data.length;

      this.$el.attr('data-total', count);
      // if (this.model.length > 0) {
      //   this.$el.attr('data-total', count);
      // } else {
      //   this.$el.removeAttr('data-total');
      // }
    }
  });

});
