var React = require('react/addons');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var SearchInterface = require('./components/search/SearchInterface.js');
var DictInterface = require('./components/dict/DictInterface.js');
var ConfigInterface = require('./components/config/ConfigInterface.js');
var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      config: {
        limit: 1000,
        evidenceLimit: 10,
        hideAdded: false,
        rowsPerPage: 25
      },
      results: {
        rows: [],
        qexpr: null,
        pending: false,
        request: null,
        errorMessage: null
      },
      tables: {
      },
      target: null
    };
  },
  render: function() {
    var tables = this.linkState('tables');
    var target = this.linkState('target');
    var results = this.linkState('results');
    var config = this.linkState('config');
    var searchInterface = 
      <SearchInterface config={config} results={results} tables={tables} target={target}/>;
    var dictInterface = <DictInterface target={target} tables={tables}/>;
    var configInterface = <ConfigInterface config={config}/>;
    return (
      <div>
        <TabbedArea animation={false}>
          <TabPane className="mainContent" eventKey={1} tab="Search">
            {searchInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={2} tab="Dictionaries">
            {dictInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={3} tab="Configuration">
            {configInterface}
          </TabPane>
        </TabbedArea>
      </div>
    );
  }
});
React.render(<DictApp/>, document.body);
