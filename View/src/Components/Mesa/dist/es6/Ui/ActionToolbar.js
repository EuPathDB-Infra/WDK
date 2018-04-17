'use strict';var _createClass=function(){function defineProperties(target,props){for(var descriptor,i=0;i<props.length;i++)descriptor=props[i],descriptor.enumerable=descriptor.enumerable||!1,descriptor.configurable=!0,'value'in descriptor&&(descriptor.writable=!0),Object.defineProperty(target,descriptor.key,descriptor)}return function(Constructor,protoProps,staticProps){return protoProps&&defineProperties(Constructor.prototype,protoProps),staticProps&&defineProperties(Constructor,staticProps),Constructor}}(),_react=require('react'),_react2=_interopRequireDefault(_react),_propTypes=require('prop-types'),_propTypes2=_interopRequireDefault(_propTypes),_SelectionCounter=require('../Ui/SelectionCounter'),_SelectionCounter2=_interopRequireDefault(_SelectionCounter);Object.defineProperty(exports,'__esModule',{value:!0});function _interopRequireDefault(obj){return obj&&obj.__esModule?obj:{default:obj}}function _classCallCheck(instance,Constructor){if(!(instance instanceof Constructor))throw new TypeError('Cannot call a class as a function')}function _possibleConstructorReturn(self,call){if(!self)throw new ReferenceError('this hasn\'t been initialised - super() hasn\'t been called');return call&&('object'==typeof call||'function'==typeof call)?call:self}function _inherits(subClass,superClass){if('function'!=typeof superClass&&null!==superClass)throw new TypeError('Super expression must either be null or a function, not '+typeof superClass);subClass.prototype=Object.create(superClass&&superClass.prototype,{constructor:{value:subClass,enumerable:!1,writable:!0,configurable:!0}}),superClass&&(Object.setPrototypeOf?Object.setPrototypeOf(subClass,superClass):subClass.__proto__=superClass)}var ActionToolbar=function(_React$PureComponent){function ActionToolbar(props){_classCallCheck(this,ActionToolbar);var _this=_possibleConstructorReturn(this,(ActionToolbar.__proto__||Object.getPrototypeOf(ActionToolbar)).call(this,props));return _this.dispatchAction=_this.dispatchAction.bind(_this),_this.renderActionItem=_this.renderActionItem.bind(_this),_this.renderActionItemList=_this.renderActionItemList.bind(_this),_this}return _inherits(ActionToolbar,_React$PureComponent),_createClass(ActionToolbar,[{key:'getSelection',value:function getSelection(){var _props=this.props,rows=_props.rows,options=_props.options,isRowSelected=options.isRowSelected;return'function'==typeof isRowSelected?rows.filter(isRowSelected):[]}},{key:'dispatchAction',value:function dispatchAction(action){var handler=action.handler,callback=action.callback,_props2=this.props,rows=_props2.rows,columns=_props2.columns,selection=this.getSelection();if((!action.selectionRequired||selection.length)&&('function'==typeof handler&&selection.forEach(function(row){return handler(row,columns)}),'function'==typeof callback))return callback(selection,columns,rows)}},{key:'renderActionItem',value:function renderActionItem(_ref){var _this2=this,action=_ref.action,element=action.element,selection=this.getSelection(),className='ActionToolbar-Item'+(action.selectionRequired&&!selection.length?' disabled':'');'string'==typeof element||_react2.default.isValidElement(element)||'function'!=typeof element||(element=element(selection));return _react2.default.createElement('div',{key:action.__id,className:className,onClick:function handler(){return _this2.dispatchAction(action)}},element)}},{key:'renderActionItemList',value:function renderActionItemList(_ref2){var actions=_ref2.actions,ActionItem=this.renderActionItem;return _react2.default.createElement('div',null,actions?actions.filter(function(action){return action.element}).map(function(action,idx){return _react2.default.createElement(ActionItem,{action:action,key:idx})}):null)}},{key:'render',value:function render(){var _props3=this.props,rows=_props3.rows,actions=_props3.actions,eventHandlers=_props3.eventHandlers,_ref3=eventHandlers?eventHandlers:{},onRowSelect=_ref3.onRowSelect,onRowDeselect=_ref3.onRowDeselect,ActionList=this.renderActionItemList,selection=this.getSelection();return _react2.default.createElement('div',{className:'Toolbar ActionToolbar'},_react2.default.createElement('div',{className:'ActionToolbar-Info'},_react2.default.createElement(_SelectionCounter2.default,{rows:rows,selection:selection,onRowSelect:onRowSelect,onRowDeselect:onRowDeselect})),_react2.default.createElement(ActionList,{actions:actions}))}}]),ActionToolbar}(_react2.default.PureComponent);ActionToolbar.propTypes={rows:_propTypes2.default.array,actions:_propTypes2.default.array,eventHandlers:_propTypes2.default.object},exports.default=ActionToolbar;