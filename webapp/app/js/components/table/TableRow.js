var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../TableManager.js');
var TableRow = React.createClass({
  valueCell: function(value, i) {
    var valueString = TableManager.valueString(value);
    return <td key={i}>{valueString}</td>;
  },
  render: function() {
    var rowData = this.props.row;
    var values = rowData.values;
    var cells = values.map(this.valueCell);
    var row = <tr>{cells}</tr>;
    return row;
  }
});
module.exports = TableRow;
