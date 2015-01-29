var React = require('react');
var bs = require('react-bootstrap');
var Table = bs.Table;
var Button = bs.Button;
var PageItem = bs.PageItem;
var Pager = bs.Pager;
var KeyedBlackLabResults = require('./KeyedBlackLabResults.js');

var GroupedBlackLabResult = React.createClass({
  render: function() {
    var result = this.props.result;
    var add = this.props.callbacks.addEntry;
    var target = this.props.targetDictionary;
    var addEntry = function(e) { add(result.key) };
    var button = (target == null) ? null : <button onClick={addEntry}>{target}</button>;
    return (
      <tr>
        <td>{button}</td>
        <td>{result.key}</td>
        <td>{result.size}</td>
        <td>
          <KeyedBlackLabResults keyedResults={result.group}/>
        </td>
      </tr>
    );
  }
});

var GroupedBlackLabResults = React.createClass({
  startAt: function() {
    return this.state.currentPage * this.state.resultsPerPage;
  },
  numPages: function() {
    var n = this.props.results.length;
    var k = 1.0 * this.state.resultsPerPage;
    return Math.ceil(n / k);
  },
  pageTo: function(i) {
    if (i >= 0 && i < this.numPages()) {
      this.setState({currentPage: i});
    }
  },
  getInitialState: function() {
    return {
      resultsPerPage: 50,
      currentPage: 0
    };
  },
  hasNextPage: function() {
    return this.state.currentPage < this.numPages() - 1;
  },
  hasPrevPage: function() {
    return this.state.currentPage > 0;
  },
  pageResults: function() {
    var start = this.startAt();
    return this.props.results.slice(start, start + this.state.resultsPerPage);
  },
  bySize: function(r1, r2) {
    var diff = r2.size - r1.size;
    if (diff == 0) {
      return r1.key > r2.key ? 1 : -1;
    } else {
      return diff;
    }
  },
  sortResults: function() {
    this.props.results.sort(this.bySize);
  },
  makeRow: function(result) {
    return (
      <GroupedBlackLabResult
        key={result.key}
        result={result}
        callbacks={this.props.callbacks}
        targetDictionary={this.props.targetDictionary}/>
    );
  },
  makePager: function() {
    var numPages = this.numPages();
    if (numPages > 1) {
      var currentPage = this.state.currentPage;
      var pageTo = this.pageTo;
      var next = function() { pageTo(currentPage + 1); };
      var prev = function() { pageTo(currentPage - 1); };
      return (
        <Pager>
          <PageItem previous disabled={!this.hasPrevPage()} onClick={prev}>
            &larr; Previous
          </PageItem>
          <PageItem next disabled={!this.hasNextPage()} onClick={next}>
            Next &rarr; 
          </PageItem>
        </Pager>
      );
    } else {
      return null;
    }
  },
  render: function() {
    this.sortResults();
    var pager = this.makePager();
    return (
      <div>
        {pager}
        <Table striped bordered condensed hover>
          <thead>
            <tr>
              <th>&nbsp;</th>
              <th>Group</th>
              <th>Count</th>
              <th>Evidence</th>
            </tr>
          </thead>
          <tbody>
            {this.pageResults().map(this.makeRow)}
          </tbody>
        </Table>
        {pager}
      </div>
    );
  }
});
module.exports = GroupedBlackLabResults;
