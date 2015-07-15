var React = require('react/addons');
var bs = require('react-bootstrap');
var SearchInterface = require('./components/search/SearchInterface.js');
var TablesInterface = require('./components/table/TablesInterface.js');
var TableManager = require('./managers/TableManager.js');
var PatternsInterface = require('./components/pattern/PatternsInterface.js');
var ConfigInterface = require('./components/config/ConfigInterface.js');
var HelpInterface = require('./components/help/HelpInterface.js');
var xhr = require('xhr');
var Router = require('react-router');
var { Route, DefaultRoute, Redirect, RouteHandler, Link } = Router;
const Header = require('./components/Header.js');
const AuthStore = require('./stores/AuthStore.js');
const CorporaStore = require('./stores/CorporaStore.js');
const assign = require('object-assign');

var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  contextTypes: {
    router: React.PropTypes.func
  },

  componentWillUnmount() {
    AuthStore.removeChangeListener(this.onAuthChange);
  },

  componentDidMount() {
    AuthStore.addChangeListener(this.onAuthChange);

    TableManager.addChangeListener(function(tables) {
      var target = this.linkState('target');
      this.setState({tables: tables});
      if(target.value == null && tables) {
        for(var tableName in tables) {
          if(tables.hasOwnProperty(tableName)) {
            target.requestChange(tableName);
            break;
          }
        }
      }
    }.bind(this));
    TableManager.setUserEmail(AuthStore.getUserEmail());
  },

  getInitialState() {
    return {
      authenticated: AuthStore.authenticated(),
      config: {
        limit: 1000,
        evidenceLimit: 10,
        hideAdded: false,
        groupsPerPage: 25,
        ml: {
           disable: false,
           depth: 3,
           beamSize: 25,
           maxSampleSize: 8000,
           pWeight: 2.0,
           nWeight: -1.0,
           uWeight: -0.05,
           pWeightNarrow: 2.0,
           nWeightNarrow: -1.0,
           uWeightNarrow: -0.05
        }
      },
      tables: [],
      target: null
    };
  },

  onAuthChange() {
    let newState = { authenticated: AuthStore.authenticated() };
    if (!AuthStore.authenticated()) {
      assign(newState, { target: null });
    }
    this.setState(newState);
    TableManager.setUserEmail(AuthStore.getUserEmail());
  },

  renderContent() {
    var target = this.linkState('target');
    var patterns = this.linkState('patterns');
    var config = this.linkState('config');
    var router = this.context.router;
    var searchClass = (router.isActive('search')) ? 'active' : null;
    var tablesClass = (router.isActive('tables')) ? 'active' : null;
    var patternsClass = (router.isActive('patterns')) ? 'active' : null;
    var configClass = (router.isActive('config')) ? 'active' : null;
    var helpClass = (router.isActive('help')) ? 'active' : null;
    return (
      <div>
        <nav className="nav nav-tabs">
          <li className={searchClass}><Link to="search">Search</Link></li>
          {(this.state.authenticated) ? <li className={tablesClass}><Link to="tables">Tables</Link></li> : null}
          <li className={patternsClass}><Link to="patterns">Patterns</Link></li>
          <li className={configClass}><Link to="config">Config</Link></li>
          <li className={helpClass}><Link to="help">Help</Link></li>
        </nav>
        <div className="container-fluid">
          <RouteHandler
            authenticated={this.state.authenticated}
            config={config} 
            target={target} />
        </div>
      </div>
    );
  },

  render() {
    var content = this.renderContent();
    return <div><Header authenticated={this.state.authenticated}/>{content}</div>;
  }
});

var routes = (
  <Route handler={DictApp}>
    <Route name="search" path="search" handler={SearchInterface}/>
    <Route name="tables" path="tables" handler={TablesInterface}/>
    <Route name="patterns" path="patterns" handler={PatternsInterface}/>
    <Route name="config" path="config" handler={ConfigInterface}/>
    <Route name="help" path="help" handler={HelpInterface}/>
    <Redirect from="/" to="search" />
  </Route>
);

Router.run(routes, function (Handler, state) {
  React.render(<Handler/>, document.body);
});
