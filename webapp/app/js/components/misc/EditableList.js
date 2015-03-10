var React = require('react/addons');
var bs = require('react-bootstrap');
var DeleteButton = require('./DeleteButton.js');
var Input = bs.Input;
var ListGroup = bs.ListGroup;
var ListGroupItem = bs.ListGroupItem;
var EditableList = React.createClass({
  getInitialState: function() {
    return {
      input: "",
      items: []
    };
  },
  handleChange: function(e) {
    if (e.which == 13) {
      this.handleSubmit();
    } else {
      this.setState({input: e.target.value});
    }
  },
  handleSubmit: function() {
    var input = this.state.input;
    var items = this.state.items;
    var index = items.indexOf(input);
    if (index < 0) {
      items.push(input);
      this.setState({input: "", items: items}, this.focusAndUpdate);
    }
  },
  focusAndUpdate: function() {
    this.updateListener();
    this.refs.inputBox.getDOMNode().childNodes[0].focus();
  },
  updateListener: function() {
    this.props.onChange(this.state.items);
  },
  makeRow: function(value) {
    var button = <DeleteButton callback={this.deleteRow(value)}/>;
    return <ListGroupItem key={value}>{value} {button}</ListGroupItem>;
  },
  deleteRow: function(value) {
    return function() {
      var index = this.state.items.indexOf(value);
      if (index >= 0) {
        this.state.items.splice(index, 1);
        this.setState({items: this.state.items}, this.updateListener);
      }
    }.bind(this);
  },
  render: function() {
    var name = this.props.name;
    var items = this.state.items;
    var input = this.state.input;
    var placeholder = "Add to " + name;
    var inputBox = <Input type="text" ref="inputBox" placeholder={placeholder}
      onKeyPress={this.handleChange} onChange={this.handleChange} value={input}/>;
    var groupItems = this.state.items.map(this.makeRow);
    var listGroup = <ListGroup>{groupItems}</ListGroup>;
    var listGroupCond = items.length > 0 ? listGroup : null;
    return (
      <div>
        {listGroupCond}
        {inputBox}
      </div>
    );
  }
});
module.exports = EditableList;
