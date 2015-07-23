const React = require('react/addons');
const bs = require('react-bootstrap');
const DeleteButton = require('./DeleteButton.js');
const Input = bs.Input;
const ListGroup = bs.ListGroup;
const ListGroupItem = bs.ListGroupItem;
const Glyphicon = bs.Glyphicon;
const Button = bs.Button;
const Well = bs.Well;

var EditableList = React.createClass({
  getInitialState: function() {
    return {input: ""};
  },

  pressedEnterKey: function(e) {
    return e.which == 13;
  },

  handleChange: function(e) {
    if (this.pressedEnterKey(e)) {
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
    var items = this.props.value;
    var input = this.state.input;
    const plusIcon = <Button onClick={this.add} disabled={this.props.disabled}>
      <Glyphicon glyph='plus'/>
    </Button>;
    var inputBox = <Input
      type="text"
      ref="inputBox"
      placeholder={"Add column"}
      onKeyPress={this.handleChange}
      onChange={this.handleChange}
      value={input}
      buttonAfter={plusIcon}
      disabled={this.props.disabled} />;
    var groupItems = items.map(this.makeRow);
    var listGroup = groupItems.length > 0 ?
      <ListGroup>{groupItems}</ListGroup> :
      <Well disabled={this.props.disabled} bsSize='small'>No columns defined yet.</Well>;

    return (
      <div>
        {listGroup}
        {inputBox}
      </div>
    );
  }

});
module.exports = EditableList;
