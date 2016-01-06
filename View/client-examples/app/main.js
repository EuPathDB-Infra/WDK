import React from 'react';
import { render } from 'react-dom';

let req = require.context('./examples', true, /^\.\/.*\.js$/);

class App extends React.Component {

  renderExample() {
    if (this.props.example != '') {
      let { Example } = req(this.props.example);
      return <Example/>
    }
  }

  render() {
    return (
      <div>
        <div id="sidebar">
          <h2>Examples</h2>
          <ul>
            {req.keys().map(key => (
              <li key={key}>
                <a href={'#' + key}>{key}</a>
              </li>
            ))}
          </ul>
        </div>

        <div id="main">
          {this.renderExample()}
        </div>
      </div>
    );
  }

}

function handleUrl() {
 render(<App example={location.hash.slice(1)}/>, document.getElementById('app')); 
}

window.onhashchange = handleUrl;

handleUrl();