var React = require('react/addons');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Panel = bs.Panel;
var Modal = bs.Modal;
var Button = bs.Button;
var Alert = bs.Alert;
var EditableList = require('../misc/EditableList.js');
var TableManager = require('../../managers/TableManager.js');
var TableAdder = React.createClass({
  componentDidMount: function() {
    var callback = function() {
      // Since this is a callback, the component could have been unmounted in the meantime.
      if(this.isMounted()) {
        if (TableManager.userEmail()) {
          this.setState({error: null});
        } else {
          this.setState({error: "You must be logged in to create tables."});
        }
      }
    }.bind(this)

    TableManager.addChangeListener(callback);
    callback();
  },
  getInitialState: function() {
    return {name: '', cols: [], error: null};
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
    try {
      this.props.onSubmit(table);
    } catch(err) {
      alert(err); // TODO: replace with something pretty
    }
    this.setState({name: '', cols: []});
  },
  submitDisabled: function() {
    var name = this.state.name;
    var cols = this.state.cols;
    return name.trim() == '' || cols.length == 0 || this.state.error;
  },
  nameInput: function() {
    var label = "Table Name";
    var placeholder = "Enter Table Name";
    var onChange = this.handleNameChange;
    var value = this.state.name;
    return <Input
      key="name"
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
    return <div key="columnList">{label}{list}</div>;
  },
  submitButton: function() {
    return <Input
      key="submitButton"
      type="submit"
      value="Create Table"
      disabled={this.submitDisabled()}
      onClick={this.handleSubmit}/>;
  },
  render: function() {
    var nameInput = this.nameInput();
    var columnInput = this.columnInput();
    var submitButton = this.submitButton();

    var content = [nameInput, columnInput];
    if(this.state.error) content.push(<Alert key="error" bsStyle="danger">{this.state.error}</Alert>);
    content.push(submitButton);

    return <div>{content}</div>;
  }
});
module.exports = TableAdder;
