var React = require('react/addons');
var bs = require('react-bootstrap');
var DeleteButton = require('./DeleteButton.js');
var Input = bs.Input;
var ListGroup = bs.ListGroup;
var ListGroupItem = bs.ListGroupItem;
var EditableList = React.createClass({
  getInitialState: function() {
    return {input: ""};
  },
  handleChange: function(e) {
    if (e.which == 13) {
      this.add();
    } else {
      this.setState({input: e.target.value});
    }
  },
  add: function() {
    var input = this.state.input;
    this.props.onAdd(input);
    this.setState({input: ''});
  },
  remove: function(i) {
    return function () { this.props.onRemove(i); }.bind(this);
  },
  focus: function() {
    this.refs.inputBox.getDOMNode().childNodes[0].focus();
  },
  makeRow: function(value, i) {
    var button = <DeleteButton callback={this.remove(i)}/>;
    var key = value + '.' + i;
    return <ListGroupItem key={key}>{value} {button}</ListGroupItem>;
  },
  render: function() {
    var name = this.props.name;
    var items = this.props.value;
    var input = this.state.input;
    var placeholder = "Add to " + name;
    var inputBox = <Input type="text" ref="inputBox" placeholder={placeholder}
      onKeyPress={this.handleChange} onChange={this.handleChange} value={input}/>;
    var groupItems = items.map(this.makeRow);
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
