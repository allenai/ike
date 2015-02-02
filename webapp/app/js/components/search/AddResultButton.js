var React = require('react');
var bs = require('react-bootstrap');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var AddResultButton = React.createClass({
  target: function() {
    return this.props.target.value;
  },
  dict: function() {
    var target = this.target();
    var dicts = this.props.dicts.value;
    if (dicts != null && target in dicts) {
      return dicts[target];
    } else {
      return null;
    }
  },
  entry: function() {
    return this.props.row.key;
  },
  toggle: function(type) {
    var entry = this.entry();
    var dict = this.dict();
    var i = dict[type].indexOf(entry);
    if (i >= 0) {
      dict[type].splice(i, 1);
    } else {
      dict[type].unshift(entry);
    }
    this.props.dicts.requestChange(this.props.dicts.value);
  },
  togglePos: function() {
    this.toggle("positive");
  },
  toggleNeg: function() {
    this.toggle("negative");
  },
  hasType: function(type) {
    return this.dict()[type].indexOf(this.entry()) >= 0;
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
    var dicts = this.props.dicts;
    if (target.value == null) {
      return null;
    }
    var style = {};
    return (
      <ButtonToolbar>
        <ButtonGroup bsSize="small">
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
