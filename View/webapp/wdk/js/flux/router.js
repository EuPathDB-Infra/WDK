import React from 'react';
import Router from 'react-router';
import App from './App';
import IndexPage from './controllers/IndexPage';
import NotFoundPage from './controllers/NotFoundPage';
import AnswerPage from './controllers/AnswerPage';
import QuestionListPage from './controllers/QuestionListPage';

var { Route, DefaultRoute, NotFoundRoute } = Router;

// export default Router.create({ routes });

/**
 * This is a transitional routes definition. Notice that there is not top-level
 * route, and that this is an array.
 *
 * Example usage:
 *
 *     Router.run(routes, '/project', function(Handler) {
 *       React.render(<Handler/>, document.getElementById("app"));
 *     });
 *
 */
export var routes = [
  <Route name="answer" path="answer/:questionName" handler={AnswerPage}/>,
  <Route name="question-list" handler={QuestionListPage}/>,
  <DefaultRoute handler={IndexPage}/>,
  <NotFoundRoute handler={NotFoundPage}/>,
];


/**
 * Example of a "wholistic" routes definition. App defines the basic page
 * layout.
 */
export var appRoutes = (
  <Route name="app" path="/" handler={App}>
    {routes}
  </Route>
);
