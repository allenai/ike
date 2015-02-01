var React = require('react/addons');
var SearchInterface = require('./components/search/SearchInterface.js');
var ResultsInterface = require('./components/ResultsInterface.js');
var DictInterface = require('./components/dict/DictInterface.js');

var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      results: [],
      dicts: {},
      target: null
    };
  },
  render: function() {
    var dicts = this.linkState('dicts');
    var target = this.linkState('target');
    var results = this.linkState('results');
    var searchInterface = <SearchInterface resultsLink={results}/>;
    var dictInterface = <DictInterface target={target} dicts={dicts}/>;
    var resultsInterface = <ResultsInterface dicts={dicts} results={results}/>;
    return (
      <div>
        {searchInterface}
        <div className="fluid" style={{margin: 20}}>
          <div className="row">
            <div className="col-md-3">{dictInterface}</div>
            <div className="col-md-9">{resultsInterface}</div>
          </div>
        </div>
      </div>
    );
  }
});
React.render(<DictApp/>, document.body);
