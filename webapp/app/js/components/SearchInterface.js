var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;

var DictionarySelector = React.createClass({
  setTargetDictionary: function(e) {
    this.props.callbacks.setTargetDictionary(e.target.value);
  },
  render: function() {
    var dicts = Object.keys(this.props.dictionaries);
    if (dicts.length == 0) {
      return null;
    }
    var target = this.props.targetDictionary;
    var makeDict = function(name) {
      return <option key={name} value={name}>{name}</option>;
    }
    return (
      <Input type="select" label="Target Dictionary" onChange={this.setTargetDictionary} value={target}>
        {dicts.map(makeDict)}
      </Input>
    );
  }
});

var SearchInterface = React.createClass({
  getInitialState: function() {
    return {query: "(JJ) information extraction", limit: 100, evidenceLimit: 1};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callbacks.executeSearch(this.state);
  },
  onPatternChange: function(e) {
    this.setState({query: e.target.value});
  },
  onLimitChange: function(e) {
    this.setState({limit: parseInt(e.target.value)});
  },
  onEvidenceLimitChange: function(e) {
    this.setState({evidenceLimit: parseInt(e.target.value)});
  },
  render: function() {
    return (
      <form onSubmit={this.handleSubmit}>
        <DictionarySelector callbacks={this.props.callbacks} targetDictionary={this.props.targetDictionary} dictionaries={this.props.dictionaries}/>
        <Input type="text" label="Input Pattern" onChange={this.onPatternChange} value={this.state.query}/> 
        <Input type="select" label="Maximum Number of Rows" onChange={this.onLimitChange} value={this.state.limit}>
          <option value="10">10</option>
          <option value="100">100</option>
          <option value="200">200</option>
          <option value="500">500</option>
          <option value="1000">1000</option>
          <option value="10000">10000</option>
        </Input>
        <Input type="select" label="Maximum Number of Evidence per Row" onChange={this.onEvidenceLimitChange} value={this.state.evidenceLimit}>
          <option value="1">1</option>
          <option value="5">5</option>
          <option value="10">10</option>
          <option value="100">100</option>
          <option value="1000">1000</option>
          <option value="10000">10000</option>
        </Input>
        <Input type="submit" value="Search"/>
      </form>
    );
  }
});
module.exports = SearchInterface;
