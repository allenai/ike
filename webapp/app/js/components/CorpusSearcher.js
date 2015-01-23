var React = require('react');
var xhr = require('xhr');
var SearchInterface = require('./SearchInterface.js');
var BlackLabResults = require('./BlackLabResults.js');
var CorpusSearcher = React.createClass({
  getInitialState: function() {
    return {results: []};
  },
  executeSearch: function(queryObj) {
    xhr({
      body: JSON.stringify(queryObj),
      uri: '/api/search',
      headers: {
        'Content-Type': 'application/json'
      },
      method: 'POST'
    }, function(err, resp, body) {
      this.setState({results: JSON.parse(body)});
    }.bind(this));
  },
  render: function() {
    return (
      <div>
        <SearchInterface callback={this.executeSearch}/>
        <BlackLabResults results={this.state.results}/>
      </div>
    );
  }
});
module.exports = CorpusSearcher;
