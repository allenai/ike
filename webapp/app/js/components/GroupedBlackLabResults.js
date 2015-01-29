var React = require('react');
var bs = require('react-bootstrap');
var Table = bs.Table;
var Pager = bs.Pager;
var Button = bs.Button;
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var PageItem = bs.PageItem;
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

var GroupedBlackLabPager = React.createClass({
  render: function() {
    var numPages = this.props.numPages;
    var currentPage = this.props.currentPage;
    var callback = this.props.callback;
    var makeButton = function(i) {
      var disabled = i == currentPage;
      var handleClick = function(e) { e.preventDefault(); callback(i); };
      var button = <Button
        className="pageButton"
        bsSize="small"
        key={"page" + i}
        href="#"
        disabled={disabled}
        onClick={handleClick}>{i+1}</Button>;
      return button;
    };
    var allButtons = [];
    for (var i = 0; i < numPages; i++) {
      allButtons.push(makeButton(i));
    }

    var wsize = 3;
    var buttons = [];
    var dots = <Button className="pageButton" bsSize="small" disabled>&middot;&middot;&middot;</Button>;

    if (currentPage - wsize <= 1) {
      buttons = buttons.concat(allButtons.slice(0, currentPage));
    } else {
      var left = allButtons.slice(currentPage - wsize, currentPage);
      buttons = buttons.concat([allButtons[0], dots], left);
    }
    if (currentPage + wsize >= numPages) {
      buttons = buttons.concat(allButtons.slice(currentPage, numPages));
    } else {
      var right = allButtons.slice(currentPage, currentPage + wsize);
      buttons = buttons.concat(right, [dots, allButtons.slice(numPages-1, numPages)]);
    }
    var hasPrev = currentPage != 0;
    var hasNext = currentPage < numPages - 1;
    if (numPages > 1) {
      var nextFn = function(e) { e.preventDefault(); callback(currentPage + 1); };
      var prevFn = function(e) { e.preventDefault(); callback(currentPage - 1); };
      var prev = <Button className="pageButton" bsSize="small" key="pageprev" href="#" disabled={!hasPrev} onClick={prevFn}>Prev</Button>;
      var next = <Button className="pageButton" bsSize="small" key="pagenext" href="#" disabled={!hasNext} onClick={nextFn}>Next</Button>;

      return (
        <ButtonToolbar justified>
          <ButtonGroup>{prev}</ButtonGroup>
          <ButtonGroup>{buttons}</ButtonGroup>
          <ButtonGroup>{next}</ButtonGroup>
        </ButtonToolbar>
        );
    } else {
      return <div></div>;
    }
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
      resultsPerPage: 50,
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
      var diff = r2.size - r1.size;
      if (diff == 0) {
        return r1.key > r2.key ? 1 : -1;
      } else {
        return diff;
      }
    });
    var makeRow = function(r) {
      return <GroupedBlackLabResult key={r.key} result={r} callbacks={this.props.callbacks} targetDictionary={this.props.targetDictionary}/>;
    }.bind(this);
    var pager = <GroupedBlackLabPager numPages={this.numPages()} currentPage={this.state.currentPage} callback={this.toPage}/>;
    return (
      <div>
        {pager}
        <Table striped bordered condensed hover>
          <thead>
            <tr>
              <th>&nbsp;</th>
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
