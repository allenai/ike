var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var Input = bs.Input;
var TargetSelector = React.createClass({
  makeOption: function(name) {
    return <option value={name} key={name}>{name}</option>;
  },
  render: function() {
    var target = this.props.target;
    var names = Object.keys(TableManager.getTables());
    var label = "Target Table";
    if (names.length > 0) {
      return (
        <Input label={label} type="select" valueLink={target}>
          {names.map(this.makeOption)}
        </Input>
      );
    } else {
      return (
        <Input label={label} type="select" disabled>
          <option>No Tables</option>
        </Input>
      );
    }
  }
});
module.exports = TargetSelector;
