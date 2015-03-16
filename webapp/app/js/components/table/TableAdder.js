var React = require('react/addons');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Panel = bs.Panel;
var EditableList = require('../misc/EditableList.js');
var TableAdder = React.createClass({
  getInitialState: function() {
    return {name: '', cols: []};
  },
  validCol: function(col) {
    return col && col.trim() && this.state.cols.indexOf(col) < 0;
  },
  addCol: function(value) {
    if (this.validCol(value)) {
      this.state.cols.push(value);
      this.setState({cols: this.state.cols});
    }
  },
  removeCol: function(i) {
    this.state.cols.splice(i, 1);
    this.setState({cols: this.state.cols});
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
    this.props.onSubmit(table);
    this.setState({name: '', cols: []});
  },
  submitDisabled: function() {
    var name = this.state.name;
    var cols = this.state.cols;
    return name.trim() == '' || cols.length == 0;
  },
  nameInput: function() {
    var label = "Table Name";
    var placeholder = "Enter Table Name";
    var onChange = this.handleNameChange;
    var value = this.state.name;
    return <Input
      type="text"
      label={label}
      value={this.state.name}
      placeholder={placeholder}
      onChange={onChange}/>;
  },
  columnInput: function() {
    var name = "Table Columns";
    var label = <label className="control-label">{name}</label>;
    var list = <EditableList
      name={name}
      onAdd={this.addCol}
      onRemove={this.removeCol}
      value={this.state.cols}/>;
    return <div>{label}{list}</div>;
  },
  submitButton: function() {
    return <Input
      type="submit"
      value="Create Table"
      disabled={this.submitDisabled()}
      onClick={this.handleSubmit}/>;
  },
  render: function() {
    var nameInput = this.nameInput();
    var columnInput = this.columnInput();
    var submitButton = this.submitButton();
    return <div>{nameInput}{columnInput}{submitButton}</div>;
  }
});
module.exports = TableAdder;
