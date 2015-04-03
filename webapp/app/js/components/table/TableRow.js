var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var DeleteButton = require('../misc/DeleteButton.js');
var ProvenanceButton = require('../misc/ProvenanceButton.js');
var TableRow = React.createClass({
  valueCell: function(value, i) {
    var valueString = TableManager.valueString(value);
    return <td key={i}>{valueString}</td>;
  },
  deleteButton: function() {
    var table = this.props.table;
    var rowType = this.props.rowType;
    var row = this.props.row;
    var callback = function() {
      TableManager.deleteRow(table.name, rowType, row);
    };
    var button = <DeleteButton callback={callback}/>;
    return button;
  },
  render: function() {
    var rowData = this.props.row;
    var values = rowData.values;
    var cells = values.map(this.valueCell);
    var provenance = <ProvenanceButton provenance={this.props.row.provenance}/>;
    var row = <tr><td>{this.deleteButton()}</td>{cells}<td>{provenance}</td></tr>;
    return row;
  }
});
module.exports = TableRow;
