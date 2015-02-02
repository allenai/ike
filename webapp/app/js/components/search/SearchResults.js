var React = require('react');
var bs = require('react-bootstrap');
var Well = bs.Well;
var Table = bs.Table;
var ResultRow = require('./ResultRow.js');
var SearchResults = React.createClass({
  getInitialState: function() {
    return {
      currentPage: 0
    };
  },
  startRow: function() {
    return this.state.currentPage * this.rowsPerPage();
  },
  rowsPerPage: function() {
    return this.props.config.value.rowsPerPage;
  },
  numPages: function() {
    var rowsPerPage = 1.0 * this.rowsPerPage;
    var rows = this.displayedRows();
    var numRows = rows.length;
    return Math.ceil(numRows / rowsPerPage);
  },
  displayRow: function(row) {
    var config = this.props.config.value;
    var result = !(config.hideAdded && this.targetHasRow(row));
    return result;
  },
  targetHasRow: function(row) {
    var target = this.props.target.value;
    if (target == null) {
      return false;
    } else {
      var dicts = this.props.dicts.value;
      var dict = dicts[target];
      var entry = row.key;
      var inPos = dict.positive.indexOf(entry) >= 0;
      var inNeg = dict.negative.indexOf(entry) >= 0;
      return inPos || inNeg;
    }
  },
  displayedRows: function() {
    var results = this.props.results.value;
    var rows = results.rows;
    return rows.filter(this.displayRow);
  },
  pageRows: function() {
    var rows = this.displayedRows();
    var start = this.startRow();
    return rows.slice(start, start + this.rowsPerPage());
  },
  pageRowComponents: function() {
    var dicts = this.props.dicts;
    var target = this.props.target;
    return this.pageRows().map(function(row) {
      return (
        <ResultRow
          row={row}
          dicts={dicts}
          target={target}/>
        );
    });
  },
  render: function() {
    var results = this.props.results;
    var config = this.props.config;
    var target = this.props.target;
    return (
      <Table striped bordered condensed hover>
        <thead>
          <tr>
            <th>Capture</th>
            <th>Count</th>
          </tr>
        </thead>
        <tbody>
          {this.pageRowComponents()}
        </tbody>
      </Table>
    );
  }
});
module.exports = SearchResults;
