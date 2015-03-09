var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var TargetSelector = React.createClass({
  makeOption: function(name) {
    return <option value={name} key={name}>{name}</option>;
  },
  render: function() {
    var tables = this.props.tables;
    var target = this.props.target;
    var names = Object.keys(tables.value);
    var label = "Target Dictionary";
    if (names.length > 0) {
      return (
        <Input label={label} type="select" valueLink={target}>
          {names.map(this.makeOption)}
        </Input>
      );
    } else {
      return (
        <Input label={label} type="select" disabled>
          <option>No Dictionaries</option>
        </Input>
      );
    }
  }
});
module.exports = TargetSelector;
