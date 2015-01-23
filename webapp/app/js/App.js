var React = require('react');
var WordData = require('./WordData.js');

var WordDataSeq = React.createClass({
  render: function() {
    var createWordData = function(wd, i) {
      return <WordData word={wd.word} attributes={wd.attributes} key={i} />;
    };
    return (
      <div className="wordDataSeq">{this.props.data.map(createWordData)}</div>
    );
  }
});

var CaptureGroup = React.createClass({
  render: function() {
    var name = this.props.name;
    var groupSeq = this.props.groupSeq;
    return (
      <td className="captureGroup">
        <div className="captureGroupName">{name}</div>
        <WordDataSeq data={groupSeq}/>
      </td>
    );
  }
});

var BlackLabResult = React.createClass({
  render: function() {
    var result = this.props.result;
    var seq = result.wordData;
    var matchOffset = result.matchOffset;
    var matchSeq = seq.slice(matchOffset[0], matchOffset[1]);
    var groupNames = Object.keys(result.captureGroups);
    var createGroup = function(name) {
      var offsets = result.captureGroups[name];
      var groupSeq = seq.slice(offsets[0], offsets[1]);
      var key = result.id + name;
      return <CaptureGroup name={name} groupSeq={groupSeq} key={key}/>;
    };
    return (
      <tr className="blackLabResultRow">
      {groupNames.map(createGroup)}
      <td className="resultContext"><WordDataSeq data={seq}/></td>
      </tr>
    );
  }
});

var BlackLabResults = React.createClass({
  render: function() {
    var results = this.props.results;
    var createResult = function(result, i) {
      return <BlackLabResult result={result} key={result.id}/>;
    };
    return (
      <table className="blackLabResults">
        {results.map(createResult)}
      </table>
    );
  }
});

var SearchInterface = React.createClass({
  getInitialState: function() {
    return {query: "", limit: 100};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callback(this.state);
  },
  onChange: function(e) {
    this.setState({query: e.target.value});
  },
  render: function() {
    return (
      <form className="searchInterface" onSubmit={this.handleSubmit}>
        <input onChange={this.onChange} value={this.state.query}/>
        <button>Search</button>
      </form>
    );
  }
});

var CorpusSearcher = React.createClass({
  getInitialState: function() {
    return {results: []};
  },
  executeSearch: function(queryObj) {
    var request = {
      type: "POST",
      url: "/api/search",
      contentType: "application/json",
      data: JSON.stringify(queryObj)
    };
    $.ajax(request).success(function(results) {
      for (var i = 0; i < results.length; i++) {
        results[i]['id'] = queryObj.query + i;
      }
      this.setState({results: results});
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

var wordDataSeq1 = [
  {word: "I", attributes: {pos: "PRP", cluster: "01"}},
  {word: "like", attributes: {pos: "VBP", cluster: "10"}},
  {word: "mango", attributes: {pos: "NN", cluster: "00"}},
  {word: ".", attributes: {pos: ".", cluster: "11"}}
];

var wordDataSeq2 = [
  {word: "I", attributes: {pos: "PRP", cluster: "01"}},
  {word: "hate", attributes: {pos: "VBP", cluster: "10"}},
  {word: "those", attributes: {pos: "DT", cluster: "11"}},
  {word: "bananas", attributes: {pos: "NNS", cluster: "00"}},
  {word: ".", attributes: {pos: ".", cluster: "11"}}
];

var result1 = {wordData: wordDataSeq1, matchOffset: {start: 1, end: 3}, captureGroups: {verb: {start: 1, end: 2}, noun: {start: 2, end: 3}}};
var result2 = {wordData: wordDataSeq2, matchOffset: {start: 1, end: 4}, captureGroups: {verb: {start: 1, end: 2}, noun: {start: 2, end: 4}}};
var results = [result1, result2, result1, result2];

React.render(
  <CorpusSearcher/>,
  document.getElementById('content')
);
