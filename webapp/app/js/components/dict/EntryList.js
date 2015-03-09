var React = require('react');
var bs = require('react-bootstrap');
var ListGroup = bs.ListGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var ListGroupItem = bs.ListGroupItem;
var EntryAdder = require('./EntryAdder.js');
var tableUtils = require('../../tableUtils.js');
var EntryList = React.createClass({
  deleteEntry: function(entry) {
    var tables = this.props.tables;
    var name = this.props.name;
    var type = this.props.type;
    var i = tables.value[name][type].indexOf(entry);
    if (i >= 0) {
      tables.value[name][type].splice(i, 1);
      tables.requestChange(tables.value);
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
    var string = tableUtils.rowString(entry);
    return <ListGroupItem key={string}>{string} {button}</ListGroupItem>;
  },
  render: function() {
    var target = this.props.target;
    var tables = this.props.tables;
    var name = this.props.name;
    var type = this.props.type;
    var entries = tables.value[name][type];
    var items = entries.map(this.entryItem);
    var adder =
      <EntryAdder name={name} type={type} target={target} tables={tables}/>;
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
