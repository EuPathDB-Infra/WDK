// Primitive component for creating a popup window

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import $ from 'jquery';

type Props = {
  /** Should the popup be visible or not? */
  open: boolean;

  className?: string;

  /**
   * Element which to append the draggable container. Defaults to
   * `document.body`.
   */
  parentSelector?: () => Element;

  /**
   * Element to use to constrain dragging. If set, the popup can only be
   * dragged within the returned Element.
   */
  containerSelector?: () => Element;

  /** Should the popup be draggable? */
  draggable?: boolean;

  /**
   * Set the element to use as a drag handle. This should be a descendent of the
   * content root element. Only used if `draggable` is `true`.
   */
  dragHandleSelector?: () => Element;

  /** Content of popup */
  children: React.ReactElement<any>;
}

// TODO Replace jQueryUI plugin with react-dnd
/**
 * Popup window
 *
 * @example
 * ```
 * class App extends React.Component {
 *   render() {
 *     return (
 *       <div>
 *         <button type="button" onClick={() => this.setState({ open: true })>
 *           Open popup
 *         </button>
 *         <Popup
 *           open={this.state.open}
 *           draggable
 *         >
 *           <div>
 *            <h1>
 *              Some title
 *              <div className="buttons">
 *                <button type="button" onClick={() => this.setState({ open: false })}>
 *                  <Icon fa="close"/>
 *                </button>
 *              </div>
 *            </h1>
 *            <div>Some content</div>
 *           </div>
 *         </Popup>
 *       </div>
 *     );
 *   }
 * }
 * ```
 */
export default class Popup extends React.Component<Props> {

  static defaultProps = {
    draggable: false,
  };

  containerNode: HTMLElement;

  componentDidMount() {
    this.containerNode = document.createElement('div');
    this._callJqueryWithProps();
  }

  componentDidUpdate() {
    this._callJqueryWithProps();
  }

  componentWillUnmount() {
    $(this.containerNode).draggable('destroy');
    ReactDOM.unmountComponentAtNode(this.containerNode);
    this.containerNode.remove();
  }

  _callJqueryWithProps() {
    this.containerNode.className = this.props.className || '';
    const parent = this.props.parentSelector == null ? document.body : this.props.parentSelector();
    if (parent !== this.containerNode.parentNode) parent.appendChild(this.containerNode);
    ReactDOM.unstable_renderSubtreeIntoContainer(this, this.props.children, this.containerNode);
    const $node = $(this.containerNode)
      .draggable({
        addClasses: false,
        containment: this.props.containerSelector == null ? false : this.props.containerSelector(),
        handle: this.props.dragHandleSelector == null ? false : this.props.dragHandleSelector()
      })
      .toggle(this.props.open);

    if (this.props.open) this._updatePosition();
  }

  _updatePosition() {
    if (!this.containerNode.style.top && !this.containerNode.style.left) {
      let { firstElementChild } = this.containerNode;
      this.containerNode.style.top = '200px';
      this.containerNode.style.left = 'calc(50vw - ' +
        (firstElementChild ? firstElementChild.clientWidth / 2 : 0) + 'px';
    }
  }

  render() {
    return null;
  }

}
