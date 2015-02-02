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
        evidenceLimit: 1,
        hideAdded: true,
        rowsPerPage: 25
      },
      results: {
        rows: [],
        pending: false,
        request: null,
        errorMessage: null
      },
      dicts: {
        'stuff': {name: 'stuff', positive: [], negative: []}
      },
      target: 'stuff'
    };
  },
  render: function() {
    var dicts = this.linkState('dicts');
    var target = this.linkState('target');
    var results = this.linkState('results');
    var config = this.linkState('config');
    var searchInterface = 
      <SearchInterface config={config} results={results} dicts={dicts} target={target}/>;
    var dictInterface = <DictInterface target={target} dicts={dicts}/>;
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
