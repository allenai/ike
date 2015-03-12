var React = require('react');
var bs = require('react-bootstrap');
var BsTable = bs.Table;
var TableRow = require('./TableRow.js');
var RowAdder = require('./RowAdder.js');
var SubTable = React.createClass({
  headerCell: function(col, i) {
    return <th key={i}>{col}</th>;
  },
  thead: function() {
    var cols = this.props.table.cols;
    var cells = cols.map(this.headerCell);
    return <thead><th></th>{cells}</thead>;
  },
  row: function(row, i) {
    var rowType = this.props.rowType;
    var table = this.props.table;
    var key = "row" + i;
    return <TableRow key={key} row={row} rowType={rowType} table={table}/>;
  },
  tbody: function() {
    var rowType = this.props.rowType;
    var rows = this.props.table[rowType];
    var rowComponents = rows.map(this.row);
    var rowAdder = this.rowAdder();
    return (
      <tbody>
        {rowAdder}
        {rowComponents}
      </tbody>
    );
  },
  invalidValue: function(valueString) {
    return !valueString || (valueString.trim() == '');
  },
  invalidRow: function(valueStrings) {
    return valueStrings.map(this.invalidValue).indexOf(true) >= 0;
  },
  rowAdder: function() {
    var cols = this.props.table.cols;
    var tableName = this.props.table.name;
    var rowType = this.props.rowType;
    var add = function(valueStrings) {
      if (this.invalidRow(valueStrings)) { return; }
      var row = TableManager.stringsRow(valueStrings);
      TableManager.addRow(tableName, rowType, row);
    }.bind(this);
    return <RowAdder cols={cols} onSubmit={add}/>;
  },
  render: function() {
    var style = {borderTop: 0};
    return (
      <div className="dataTableContainer">
      <table style={style} className="dataTable">
        {this.thead()}
        {this.tbody()}
      </table>
      </div>
    );
  }
});
module.exports = SubTable;
