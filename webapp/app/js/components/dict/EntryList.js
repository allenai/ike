var React = require('react');
var bs = require('react-bootstrap');
var ListGroup = bs.ListGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var ListGroupItem = bs.ListGroupItem;
var EntryAdder = require('./EntryAdder.js');
var EntryList = React.createClass({
  deleteEntry: function(entry) {
    var dicts = this.props.dicts;
    var name = this.props.name;
    var type = this.props.type;
    var i = dicts.value[name][type].indexOf(entry);
    if (i >= 0) {
      dicts.value[name][type].splice(i, 1);
      dicts.requestChange(dicts.value);
    }
  },
  deleteButton: function(entry) {
    var deleteThis = function() { this.deleteEntry(entry); }.bind(this);
    return (
      <Button
        onClick={deleteThis}
        bsSize="xsmall"
        className="pull-right"
        bsStyle="danger">
        <Glyphicon glyph="remove"/>
      </Button>
      );
  },
  entryItem: function(entry) {
    var button = this.deleteButton(entry);
    return <ListGroupItem key={entry}>{entry} {button}</ListGroupItem>;
  },
  render: function() {
    var target = this.props.target;
    var dicts = this.props.dicts;
    var name = this.props.name;
    var type = this.props.type;
    var entries = dicts.value[name][type];
    var items = entries.map(this.entryItem);
    var adder =
      <EntryAdder name={name} type={type} target={target} dicts={dicts}/>;
    return (
      <div className="mainContent">
        <ListGroup>
          <ListGroupItem>{adder}</ListGroupItem>
          {items}
        </ListGroup>
      </div>
    );
  }
});
module.exports = EntryList;
