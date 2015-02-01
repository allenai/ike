var React = require('react/addons');
var SearchInterface = require('./components/search/SearchInterface.js');
var ResultsInterface = require('./components/ResultsInterface.js');
var DictionaryInterface = require('./components/dictionary/DictionaryInterface.js');

var DictionaryApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
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
      dictionaries: {},
      target: null
    };
  },
  render: function() {
    var dictLink = this.linkState('dictionaries');
    var targetLink = this.linkState('target');
    var resultsLink = this.linkState('results');
    var search = <SearchInterface resultsLink={resultsLink}/>;
    var dictionary = 
      <DictionaryInterface 
        targetLink={targetLink}
        dictionaryLink={dictLink}/>;
    var results = 
      <ResultsInterface
        dictionaryLink={dictLink}
        resultsLink={resultsLink}/>;
    return (
      <div>
        {search}
        <div className="fluid" style={{margin: 20}}>
          <div className="row">
            <div className="col-md-3">{dictionary}</div>
            <div className="col-md-9">{results}</div>
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
