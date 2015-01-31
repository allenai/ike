var React = require('react');
var SearchInterface = require('./components/SearchInterface.js');
var ResultsInterface = require('./components/ResultsInterface.js');
var DictionaryInterface = require('./components/dictionary/DictionaryInterface.js');

var DictionaryApp = React.createClass({
  updateDictionaries: function(updated) {
    this.setState({dictionaries: updated});
  },
  updateResults: function(updated) {
    this.setState({results: updated});
  },
  getDefaultProps: function() {
    return {};
  },
  getInitialState: function() {
    return {
      results: [],
      dictionaries: {}
    };
  },
  render: function() {
    var search = 
      <SearchInterface updateResults={this.updateResults}/>
    var dictionary = 
      <DictionaryInterface dictionaries={this.state.dictionaries}
        updateDictionaries={this.updateDictionaries}/>;
    var results = 
      <ResultsInterface
        dictionaries={this.state.dictionaries}
        results={this.state.results}
        updateDictionary={this.updateDictionary}/>;
    return (
      <div>
        {search}
        <div className="fluid">
          <div className="row">
            <div className="col-md-4">{dictionary}</div>
            <div className="col-md-8">{results}</div>
          </div>
        </div>
      </div>
    );
  }
});

React.render(
  <DictionaryApp/>,
  document.body
);
