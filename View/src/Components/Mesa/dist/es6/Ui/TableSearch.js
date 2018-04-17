'use strict';

Object.defineProperty(exports, "__esModule", {
  value: true
});

var _createClass = function () { function defineProperties(target, props) { for (var i = 0; i < props.length; i++) { var descriptor = props[i]; descriptor.enumerable = descriptor.enumerable || false; descriptor.configurable = true; if ("value" in descriptor) descriptor.writable = true; Object.defineProperty(target, descriptor.key, descriptor); } } return function (Constructor, protoProps, staticProps) { if (protoProps) defineProperties(Constructor.prototype, protoProps); if (staticProps) defineProperties(Constructor, staticProps); return Constructor; }; }();

var _react = require('react');

var _react2 = _interopRequireDefault(_react);

var _propTypes = require('prop-types');

var _propTypes2 = _interopRequireDefault(_propTypes);

var _Icon = require('../Components/Icon');

var _Icon2 = _interopRequireDefault(_Icon);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

function _possibleConstructorReturn(self, call) { if (!self) { throw new ReferenceError("this hasn't been initialised - super() hasn't been called"); } return call && (typeof call === "object" || typeof call === "function") ? call : self; }

function _inherits(subClass, superClass) { if (typeof superClass !== "function" && superClass !== null) { throw new TypeError("Super expression must either be null or a function, not " + typeof superClass); } subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } }); if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass; }

var TableSearch = function (_React$PureComponent) {
  _inherits(TableSearch, _React$PureComponent);

  function TableSearch(props) {
    _classCallCheck(this, TableSearch);

    var _this = _possibleConstructorReturn(this, (TableSearch.__proto__ || Object.getPrototypeOf(TableSearch)).call(this, props));

    _this.handleQueryChange = _this.handleQueryChange.bind(_this);
    _this.clearSearchQuery = _this.clearSearchQuery.bind(_this);
    return _this;
  }

  _createClass(TableSearch, [{
    key: 'handleQueryChange',
    value: function handleQueryChange(e) {
      var query = e.target.value;
      var onSearch = this.props.onSearch;

      if (onSearch) onSearch(query);
    }
  }, {
    key: 'clearSearchQuery',
    value: function clearSearchQuery() {
      var query = null;
      var onSearch = this.props.onSearch;

      if (onSearch) onSearch(query);
    }
  }, {
    key: 'render',
    value: function render() {
      var _props = this.props,
          options = _props.options,
          query = _props.query;
      var searchPlaceholder = options.searchPlaceholder;
      var handleQueryChange = this.handleQueryChange,
          clearSearchQuery = this.clearSearchQuery;


      return _react2.default.createElement(
        'div',
        { className: 'TableSearch' },
        _react2.default.createElement(_Icon2.default, { fa: 'search' }),
        _react2.default.createElement('input', {
          type: 'text',
          name: 'Search',
          value: searchQuery || '',
          onChange: handleQueryChange,
          placeholder: searchPlaceholder
        }),
        searchQuery && _react2.default.createElement(
          'button',
          { onClick: clearSearchQuery },
          _react2.default.createElement(_Icon2.default, { fa: 'times-circle' }),
          'Clear Search'
        )
      );
    }
  }]);

  return TableSearch;
}(_react2.default.PureComponent);

;

TableSearch.propTypes = {
  query: _propTypes2.default.string,
  options: _propTypes2.default.object,
  onSearch: _propTypes2.default.func
};

exports.default = TableSearch;