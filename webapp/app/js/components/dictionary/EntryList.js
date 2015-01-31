var React = require('react');
var bs = require('react-bootstrap');
var ListGroup = bs.ListGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var ListGroupItem = bs.ListGroupItem;
var EntryAdder = require('./EntryAdder.js');
var EntryList = React.createClass({
  deleteEntry: function(entry) {
    var update = this.props.updateDictionaries;
    var dicts = this.props.dictionaries;
    var name = this.props.name;
    var type = this.props.type;
    var i = dicts[name][type].indexOf(entry);
    if (i >= 0) {
      dicts[name][type].splice(i, 1);
      update(dicts);
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
    var dicts = this.props.dictionaries;
    var name = this.props.name;
    var type = this.props.type;
    var entries = dicts[name][type];
    var items = entries.map(this.entryItem);
    var adder =
      <EntryAdder
        name={name}
        type={type}
        dictionaries={this.props.dictionaries}
        updateDictionaries={this.props.updateDictionaries}/>;
    return (
      <div className="dictList">
        <ListGroup>
          <ListGroupItem>{adder}</ListGroupItem>
          {items}
        </ListGroup>
      </div>
    );
  }
});
module.exports = EntryList;
