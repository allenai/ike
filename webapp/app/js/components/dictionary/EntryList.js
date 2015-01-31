var React = require('react');
var bs = require('react-bootstrap');
var ListGroup = bs.ListGroup;
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
  entryItem: function(entry) {
    return <ListGroupItem key={entry}>{entry}</ListGroupItem>;
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
      <ListGroup>
        <ListGroupItem>{adder}</ListGroupItem>
        {items}
      </ListGroup>
    );
  }
});
module.exports = EntryList;
