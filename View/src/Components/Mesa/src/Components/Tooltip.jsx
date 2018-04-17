import React from 'react';

function getRealOffset (el) {
  let top = 0;
  let left = 0;
  do {
    top += el.offsetTop || 0;
    left += el.offsetLeft || 0;
    el = el.offsetParent;
  } while (el);
  return { top, left };
};

class Tooltip extends React.Component {
  constructor (props) {
    super(props);
    this.state = { showText: false }
    this.showTooltip = this.showTooltip.bind(this);
    this.hideTooltip = this.hideTooltip.bind(this);
    this.renderTextBox = this.renderTextBox.bind(this);
    this.componentDidMount = this.componentDidMount.bind(this);
  }

  componentDidMount () {
    // const { anchor } = this.refs;
    // if (!anchor) return;
    // const position = getRealOffset(anchor);
    // console.log('got position as', position);
    // this.setState({ position });
  }

  showTooltip () {
    const showText = true;
    this.setState({ showText })
  }

  hideTooltip () {
    const showText = false;
    this.setState({ showText });
  }

  renderTextBox () {
    const { text, position } = this.props;
    const { showText } = this.state;

    const wrapperStyle = {
      top: 0,
      left: 0,
      width: '100vw',
      height: '100vh',
      zIndex: 1000000,
      position: 'fixed',
      pointerEvents: 'none',
    };

    const { top, left } = position ? position : { top: 0, left: 0 };

    const textStyle = { top, left, position: 'relative' };

    return !showText ? null : (
      <div className="Tooltip-Wrapper" style={wrapperStyle}>
        <div className="Tooltip-Text" style={textStyle}>
          {text}
        </div>
      </div>
    );
  }

  render () {
    const { children } = this.props;
    const className = 'Tooltip' + (this.props.className ? ' ' + this.props.className : '');
    const TextBox = this.renderTextBox;
    return (
      <div
        className={className}
        onMouseEnter={this.showTooltip}
        onMouseLeave={this.hideTooltip}>
        {children}
        <TextBox />
      </div>
    )
  }
};

export default Tooltip;
