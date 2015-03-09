var React = require('react');
var bs = require('react-bootstrap');
var tableUtils = require('../../tableUtils.js');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var AddResultButton = React.createClass({
  target: function() {
    return this.props.target.value;
  },
  table: function() {
    var target = this.target();
    var tables = this.props.tables.value;
    if (tables != null && target in tables) {
      return tables[target];
    } else {
      return null;
    }
  },
  entry: function() {
    return this.props.row.key;
  },
  entryIndex: function(type) {
    var entry = this.entry();
    var table = this.table();
    var rowStrings = table[type].map(tableUtils.rowString);
    var i = rowStrings.indexOf(entry);
    return i;
  },
  toggle: function(type) {
    var entry = this.entry();
    var table = this.table();
    var i = this.entryIndex(type);
    if (i >= 0) {
      table[type].splice(i, 1);
    } else {
      table[type].unshift(tableUtils.stringToRow(entry));
    }
    this.props.tables.requestChange(this.props.tables.value);
  },
  togglePos: function() {
    if (!this.isPos() && this.isNeg()) {
      this.toggle("negative");
    }
    this.toggle("positive");
  },
  toggleNeg: function() {
    if (!this.isNeg() && this.isPos()) {
      this.toggle("positive");
    }
    this.toggle("negative");
  },
  hasType: function(type) {
    return this.entryIndex(type) >= 0;
  },
  isPos: function() {
    return this.hasType("positive");
  },
  isNeg: function() {
    return this.hasType("negative");
  },
  posStyle: function() {
    return this.isPos() ? 'primary' : 'default';
  },
  negStyle: function() {
    return this.isNeg() ? 'warning' : 'default';
  },
  render: function() {
    var row = this.props.row;
    var target = this.props.target;
    var tables = this.props.tables;
    if (target.value == null) {
      return null;
    }
    var style = {};
    return (
      <ButtonToolbar>
        <ButtonGroup bsSize="small" style={{display: 'flex'}}>
          <Button onClick={this.togglePos} bsStyle={this.posStyle()}>
            {target.value}
          </Button>
          <Button onClick={this.toggleNeg} bsStyle={this.negStyle()}>
            not {target.value}
          </Button>
        </ButtonGroup>
      </ButtonToolbar>
    );
  }
});
module.exports = AddResultButton;
