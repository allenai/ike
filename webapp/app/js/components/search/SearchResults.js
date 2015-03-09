var React = require('react');
var bs = require('react-bootstrap');
var tableUtils = require('../../tableUtils.js');
var Well = bs.Well;
var Table = bs.Table;
var Panel = bs.Panel;
var Pager = bs.Pager;
var PageItem = bs.PageItem;
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
  pageTo: function(i) {
    if (0 <= i && i < this.numPages()) {
      this.setState({currentPage: i});
    }
  },
  hasNextPage: function() {
    return this.state.currentPage < this.numPages() - 1;
  },
  hasPrevPage: function() {
    return this.state.currentPage > 0;
  },
  nextPage: function() {
    this.pageTo(this.state.currentPage + 1);
  },
  prevPage: function() {
    this.pageTo(this.state.currentPage - 1);
  },
  rowsPerPage: function() {
    return this.props.config.value.rowsPerPage;
  },
  numPages: function() {
    var rowsPerPage = 1.0 * this.rowsPerPage();
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
      var tables = this.props.tables.value;
      var table = tables[target];
      var entry = row.key;
      var posStrings = table.positive.map(tableUtils.rowString);
      var negStrings = table.negative.map(tableUtils.rowString);
      var inPos = posStrings.indexOf(entry) >= 0;
      var inNeg = negStrings.indexOf(entry) >= 0;
      return inPos || inNeg;
    }
  },
  bySize: function(row1, row2) {
    var diff = row2.size - row1.size;
    if (diff == 0) {
      return row1.key > row2.key ? 1 : -1;
    } else {
      return diff;
    }
  },
  displayedRows: function() {
    var results = this.props.results.value;
    var rows = results.rows;
    rows.sort(this.bySize);
    return rows.filter(this.displayRow);
  },
  pageRows: function() {
    var rows = this.displayedRows();
    var start = this.startRow();
    return rows.slice(start, start + this.rowsPerPage());
  },
  pageRowComponents: function() {
    var tables = this.props.tables;
    var target = this.props.target;
    return this.pageRows().map(function(row) {
      return (
        <ResultRow
          key={row.key}
          row={row}
          tables={tables}
          target={target}/>
        );
    });
  },
  renderTable: function() {
    var results = this.props.results;
    var config = this.props.config;
    var target = this.props.target;
    var addCol = (this.props.target.value == null) ? null : <th>Add</th>;
    var nextPage = this.hasNextPage() ? (
      <PageItem next href="#" onClick={this.nextPage}>
        Next Page &rarr;
      </PageItem>
    ) : null;
    var prevPage = this.hasPrevPage() ? (
      <PageItem previous href="#" onClick={this.prevPage}>
        &larr; Previous Page
      </PageItem>
    ) : null;
    var pager = <Pager>{nextPage} {prevPage}</Pager>;
    return (
      <div>
        <Table striped bordered condensed hover>
          <thead>
            <tr>
              {addCol}
              <th></th>
              <th>Count</th>
              <th>Context</th>
            </tr>
          </thead>
          <tbody>
            {this.pageRowComponents()}
          </tbody>
        </Table>
        {this.numPages() > 1 ? pager : null}
      </div>
    );
  },
  renderBlank: function() {
    return <div/>;
  },
  renderErrorMessage: function() {
    return (
      <Panel header="Error" bsStyle="danger">
        {this.props.results.value.errorMessage}
      </Panel>
    );
  },
  renderNoRows: function() {
    var numRows = this.props.results.value.rows.length;
    var numDisplayed = this.displayedRows().length;
    var numHidden = numRows - numDisplayed;
    return (
      <Panel header="Empty Result Set" bsStyle="warning">
        No rows to display ({numHidden} hidden).
      </Panel>
    );
  },
  renderPending: function() {
    return <div>Loading...</div>;
  },
  render: function() {
    var results = this.props.results.value;
    if (results.request == null) {
      return this.renderBlank();
    } else if (results.pending) {
      return this.renderPending();
    } else if (results.errorMessage != null) {
      return this.renderErrorMessage();
    } else if (this.displayedRows().length == 0) {
      return this.renderNoRows();
    } else {
      return this.renderTable();
    }
  }
});
module.exports = SearchResults;
