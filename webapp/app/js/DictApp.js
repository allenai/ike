var React = require('react/addons');
var bs = require('react-bootstrap');
var PageHeader = bs.PageHeader;
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var SearchInterface = require('./components/search/SearchInterface.js');
var TablesInterface = require('./components/table/TablesInterface.js');
var TableManager = require('./managers/TableManager.js');
var ConfigInterface = require('./components/config/ConfigInterface.js');
var HelpInterface = require('./components/help/HelpInterface.js');
var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  componentDidMount: function() {
    TableManager.addChangeListener(function(tables) {
      this.setState({tables: tables});
    }.bind(this));
  },
  getInitialState: function() {
    var localTables = TableManager.loadTablesFromLocalStorage();
    // Initially point to the first table name, if available.
    var tableNames = Object.keys(localTables);
    var target = tableNames.length > 0 ? tableNames[0] : null;
    return {
      config: {
        limit: 1000,
        evidenceLimit: 10,
        hideAdded: false,
        groupsPerPage: 25
      },
      results: {
        groups: [],
        qexpr: null,
        pending: false,
        request: null,
        errorMessage: null
      },
      tables: localTables,
      target: target
    };
  },
  renderContent: function() {
    var target = this.linkState('target');
    var results = this.linkState('results');
    var config = this.linkState('config');
    var searchInterface = 
      <SearchInterface config={config} results={results} target={target}/>;
    var tablesInterface = <TablesInterface target={target}/>;
    var configInterface = <ConfigInterface config={config}/>;
    var helpInterface = <HelpInterface/>; 
    return (
      <div>
        <TabbedArea animation={false}>
          <TabPane className="mainContent" eventKey={1} tab="Search">
            {searchInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={2} tab="Tables">
            {tablesInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={3} tab="Configuration">
            {configInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={4} tab="Help">
            {helpInterface}
          </TabPane>
        </TabbedArea>
      </div>
    );
  },
  renderHeader: function() {
    return (
      <div>
        <img src="assets/logo.png" width="64"/>
        <em>&ldquo;The Pacific Northwest's Cutest Extraction Tool&rdquo;</em>
      </div>
    );
  },
  render: function() {
    var content = this.renderContent();
    var header = this.renderHeader();
    return <div>{header}{content}</div>;
  }
});
React.render(<DictApp/>, document.body);
