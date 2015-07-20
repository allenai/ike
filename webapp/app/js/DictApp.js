var React = require('react/addons');
var bs = require('react-bootstrap');
var SearchInterface = require('./components/search/SearchInterface.js');
var TablesInterface = require('./components/table/TablesInterface.js');
var TableManager = require('./managers/TableManager.js');
var PatternsInterface = require('./components/pattern/PatternsInterface.js');
var ConfigInterface = require('./components/config/ConfigInterface.js');
var HelpInterface = require('./components/help/HelpInterface.js');
var xhr = require('xhr');
const Header = require('./components/Header.js');
const AuthStore = require('./stores/AuthStore.js');
const CorporaStore = require('./stores/CorporaStore.js');
const assign = require('object-assign');
const TabbedArea = bs.TabbedArea;
const TabPane = bs.TabPane;

var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

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
  },

  renderContent() {
    var target = this.linkState('target');
    var patterns = this.linkState('patterns');
    var config = this.linkState('config');
    return <TabbedArea defaultActiveKey={"search"}>
      <TabPane eventKey={"search"} tab='Search'>
        <SearchInterface config={config} target={target} />
      </TabPane>
      <TabPane eventKey={"tables"} tab='Tables'>
        <TablesInterface target={target} />
      </TabPane>
      <TabPane eventKey={"patterns"} tab='Patterns'>
        <PatternsInterface config={config} />
      </TabPane>
      <TabPane eventKey={"config"} tab='Config'>
        <ConfigInterface config={config} />
      </TabPane>
      <TabPane eventKey={"help"} tab='Help'>
        <HelpInterface/>
      </TabPane>
    </TabbedArea>;
  },

  render() {
    var content = this.renderContent();
    return <div className="container-fluid"><Header authenticated={this.state.authenticated}/>{content}</div>;
  }
});

React.render(<DictApp/>, document.body);
