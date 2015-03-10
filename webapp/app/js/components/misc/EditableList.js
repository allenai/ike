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
    this.refs.inputBox.getDOMNode().focus();
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
    var placeholder = "Add to " + name;
    var inputBox = <input type="text" ref="inputBox" placeholder={placeholder}
      onKeyPress={this.handleChange} onChange={this.handleChange}
      className="form-control"/>;
    var groupItems = this.state.items.map(this.makeRow);
    return (
      <ListGroup>
        {groupItems}
        <ListGroupItem>{inputBox}</ListGroupItem>
      </ListGroup>
    );
  }
});
module.exports = EditableList;
