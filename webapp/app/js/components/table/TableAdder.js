var React = require('react/addons');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Panel = bs.Panel;
var EditableList = require('../misc/EditableList.js');
var TableAdder = React.createClass({
  getInitialState: function() {
    return {name: '', cols: []};
  },
  handleColChange: function(cols) {
    this.setState({cols: cols});
  },
  handleNameChange: function(e) {
    this.setState({name: e.target.value});
  },
  handleSubmit: function(e) {
    e.preventDefault();
    var table = {
      name: this.state.name,
      cols: this.state.cols,
      positive: [],
      negative: []
    };
    console.log(table);
  },
  submitDisabled: function() {
    var name = this.state.name;
    var cols = this.state.cols;
    return name.trim() == '' || cols.length == 0;
  },
  render: function() {
    var form = (
      <form onSubmit={this.handleSubmit}>
        <Input type="text" label="Table Name" placeholder="Enter Table Name"
          onChange={this.handleNameChange}/>
        <label className="control-label"><span>Table Columns</span></label>
        <EditableList name="Table Columns" onChange={this.handleColChange}/>
        <Input type="submit" value="Create Table" disabled={this.submitDisabled()}/>
      </form>
    );
    return <Panel header="Create New Table">{form}</Panel>;
  }
});
module.exports = TableAdder;
