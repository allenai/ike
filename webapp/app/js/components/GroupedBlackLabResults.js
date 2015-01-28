var React = require('react');
var bs = require('react-bootstrap');
var Table = bs.Table;
var Pager = bs.Pager;
var Button = bs.Button;
var PageItem = bs.PageItem;
var KeyedBlackLabResults = require('./KeyedBlackLabResults.js');

var GroupedBlackLabResult = React.createClass({
  render: function() {
    var result = this.props.result;
    return (
      <tr>
        <td>{result.key}</td>
        <td>{result.size}</td>
        <td>
          <KeyedBlackLabResults keyedResults={result.group}/>
        </td>
      </tr>
    );
  }
});

var GroupedBlackLabPager = React.createClass({
  render: function() {
    var numPages = this.props.numPages;
    var currentPage = this.props.currentPage;
    var callback = this.props.callback;
    var makeButton = function(i) {
      var disabled = i == currentPage;
      var handleClick = function(e) { e.preventDefault(); callback(i); };
      var button = <Button
        href="#"
        disabled={disabled}
        onClick={handleClick}>{i+1}</Button>;
      return button;
    };
    var buttons = [];
    for (var i = 0; i < numPages; i++) {
      buttons.push(makeButton(i));
    }
    var hasPrev = currentPage != 0;
    var hasNext = currentPage < numPages - 1;
    if (numPages > 1) {
      var nextFn = function(e) { e.preventDefault(); callback(currentPage + 1); };
      var prevFn = function(e) { e.preventDefault(); callback(currentPage - 1); };
      var prev = <Button href="#" disabled={!hasPrev} onClick={prevFn}>Prev</Button>;
      var next = <Button href="#" disabled={!hasNext} onClick={nextFn}>Next</Button>;
      buttons.push(next);
      buttons.unshift(prev);
    }
    return <div>{buttons}</div>;
  }
});

var GroupedBlackLabResults = React.createClass({
  startAt: function() {
    return this.state.currentPage * this.state.resultsPerPage;
  },
  nextPage: function() {
    this.toPage(this.state.currentPage + 1);
  },
  prevPage: function() {
    this.toPage(this.state.currentPage - 1);
  },
  numPages: function() {
    return Math.ceil(this.props.results.length / (1.0 * this.state.resultsPerPage));
  },
  toPage: function(i) {
    if (i >= 0 && i < this.numPages()) {
      this.setState({currentPage: i});
    }
  },
  getInitialState: function() {
    return {
      resultsPerPage: 20,
      currentPage: 0
    };
  },
  hasNextPage: function() {
    return this.state.currentPage < this.numPages - 1;
  },
  hasPrevPage: function() {
    return this.state.currentPage > 0;
  },
  pageResults: function() {
    var start = this.startAt();
    return this.props.results.slice(start, start + this.state.resultsPerPage);
  },
  render: function() {
    this.props.results.sort(function(r1, r2) {
      return r2.size - r1.size;
    });
    var makeRow = function(r) {
      return <GroupedBlackLabResult key={r.key} result={r}/>;
    };
    var pager = <GroupedBlackLabPager numPages={this.numPages()} currentPage={this.state.currentPage} callback={this.toPage}/>;
    return (
      <div>
        {pager}
        <Table striped bordered condensed hover>
          <thead>
            <tr>
              <th>Group</th>
              <th># Hits</th>
              <th>Hits</th>
            </tr>
          </thead>
          <tbody>
            {this.pageResults().map(makeRow)}
          </tbody>
        </Table>
        {pager}
      </div>
    );
  }
});
module.exports = GroupedBlackLabResults;
