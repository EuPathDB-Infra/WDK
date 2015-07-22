import React from 'react';
import { Link } from 'react-router';
import QuestionStore from '../stores/questionStore';
import QuestionActions from '../actions/questionActions';
import wrappable from '../utils/wrappable';

let QuestionListController = React.createClass({

  contextTypes: {
    application: React.PropTypes.object.isRequired
  },

  componentDidMount() {
    let store = this.context.application.getStore(QuestionStore);
    let actions = this.context.application.getActions(QuestionActions);
    this.storeSubscription = store.subscribe(state => {
      this.setState(state);
    });
    actions.loadQuestions();
  },

  componentWillUnmount() {
    this.storeSubscription.dispose();
  },

  render() {
    if (!this.state) { return null; }
    let { questions, error } = this.state;

    if (error) {
      return (
        <div>There was an error: {error}</div>
      );
    } else {
      return (
        <div>
          <ol>
            {questions.map(question => (
              <li key={question.name}>
                {question.displayName + ' - '}
                <Link to="answer" params={{ questionName: question.name }}>answer page</Link>
              </li>
            ))}
          </ol>
        </div>
      );
    }
  }

});

export default wrappable(QuestionListController);
