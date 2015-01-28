var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;

var SearchInterface = React.createClass({
  getInitialState: function() {
    return {query: "(JJ) information extraction", limit: 100, evidenceLimit: 5};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callback(this.state);
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
