wdk.namespace('wdk.views.core', function(ns) {
  'use strict';

  ns.TemplateView = wdk.views.core.View.extend({

    /**
     * The name of the template to use. The name can refer to a file
     * or to a script template id.
     *
     * Template files should reside in `src/templates`.
     * Teplace scripts can reside anywhere in the DOM.
     *
     * Template files will be compiled during the build process.
     * The path of the template file will determine the object it
     * is mapped to. For instance, 'my/cool/template.hbs' will be
     * mapped to `Wdk.Templates.my.cool.template`.
     */
    templateName: null,

    template: function() {},

    model: null,

    initialize: function() {
      this.template = wdk.templates[this.templateName];
      this.listenTo(this.model, 'change', this.render);
      this.render();
    },

    render: function() {
      this.$el.html(this.template(this.model.attributes));
      return this;
    }

  });
});
