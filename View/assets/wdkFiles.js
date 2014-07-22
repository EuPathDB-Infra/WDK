/**
 * List files to be bunled here.
 *
 * Files under the src key will be combined to wdk.js.
 * Files under the libs key will be combined to wdk.libs.js.
 *
 * A file can be flagged for a specific environment, such as dev or env
 * by prepending the flag name and flag value to the filename, e.g.:
 *
 *     ...
 *     'ENV:DEV!jquery.js',
 *     'ENV:PROD!jquery.min.js',
 *     ...
 *
 * At this time, 'env' is the only flag, and 'dev' and 'prod' are the
 * only acceptable values. Flags are case-insensitve, so the following
 * will also work, which may improve readability:
 *
 *     ...
 *     'env:dev!jquery.js',
 *     'env:prod!jquery.min.js',
 *     ...
 *
 */
module.exports = {

  src: [
    // load polyfills
    'src/core/loader.js',

    'src/core/console.js',
    'src/core/namespace.js',
    'src/core/fn.js',
    'src/core/c_properties.js',
    'src/core/base_object.js',
    'src/core/runloop.js',
    'src/core/container.js',
    'src/core/application.js',
    'src/core/common.js',
    'src/core/event.js',
    'src/core/util.js',

    'src/user.js',
    'src/models/filter/field.js',
    'src/models/filter/filter.js',
    'src/models/filter/filter_service.js',

    'src/plugins/**/*.js',

    'src/components/**/*.js',

    'src/views/view.js',
    'src/views/template_view.js',
    'src/views/question_view.js',

    // filter views
    'src/views/filter/field_list_view.js',
    'src/views/filter/range_filter_view.js',
    'src/views/filter/membership_filter_view.js',
    'src/views/filter/field_detail_view.js',
    'src/views/filter/filter_fields_view.js',
    'src/views/filter/results_view.js',
    'src/views/filter/filter_item_view.js',
    'src/views/filter/filter_items_view.js',
    'src/views/filter/filter_collapsed_view.js',
    'src/views/filter/filter_expanded_view.js',
    'src/views/filter/filter_view.js',

    'src/views/strategy/**/*.js',

    'src/controllers/**/*.js',

    'src/app.js'

  ],

  libs: [
    'lib/es5-shim.min.js',
    'lib/modernizr.js',
    'lib/jquery.js',
    'ENV:DEV!lib/jquery-migrate-1.2.1.js',
    'ENV:PROD!lib/jquery-migrate-1.2.1.min.js',
    'lib/underscore-min.js',
    'lib/backbone-min.js',
    'lib/jquery-ui.js',

    'lib/jquery.cookie.js',
    'lib/jquery.blockUI.js',
    'lib/jquery.qtip.min.js',

    // question pages
    'lib/handlebars.js',
    'lib/flexigrid.js',
    'lib/chosen.jquery.min.js',
    'lib/jquery.dataTables.min.js',
    'lib/dataTables.colVis.min.js',
    'lib/jstree/jquery.jstree.js',
    'lib/spin.min.js'
  ]

};
