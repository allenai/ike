var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var Well = bs.Well;
var Table = bs.Table;
var Panel = bs.Panel;
var Pager = bs.Pager;
var PageItem = bs.PageItem;
var ButtonToolbar = bs.ButtonToolbar;
var DropdownButton = bs.DropdownButton;
var MenuItem = bs.MenuItem;
var ResultGroup = require('./ResultGroup.js');
var SearchResults = React.createClass({
  propTypes: {
    target: React.PropTypes.object,
    results: React.PropTypes.object.isRequired
  },

  target: function() {
    if(this.props.target)
      return this.props.target.value;
    else
      return null;
  },

  getInitialState: function() {
    return {
      currentPage: 0,
      orderBy: "count"
    };
  },

  startGroup: function() {
    return this.state.currentPage * this.groupsPerPage();
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

  groupsPerPage: function() {
    return this.props.config.value.groupsPerPage;
  },

  numPages: function() {
    var groupsPerPage = 1.0 * this.groupsPerPage();
    var groups = this.displayedGroups();
    var numGroups = groups.length;
    return Math.ceil(numGroups / groupsPerPage);
  },

  displayGroup: function(group) {
    var config = this.props.config.value;
    return !(config.hideAdded && this.targetHasRow(group));
  },

  targetHasRow: function(group) {
    var row = TableManager.stringsRow(group.keys);
    var target = this.target();
    if (target == null) {
      return false;
    } else {
      var hasPos = TableManager.hasPositiveRow(target, row);
      var hasNeg = TableManager.hasNegativeRow(target, row);
      return hasPos || hasNeg;
    }
  },

  displayedGroups: function() {
    var results = this.props.results;
    var groups = results.groups;

    var orderByRelevance = function(group1, group2) {
      return group2.relevanceScore - group1.relevanceScore;
    };

    var orderByCount = function(group1, group2) {
      return group2.size - group1.size;
    };

    var orderFn = orderByCount;
    if(this.state.orderBy === "relevance") {
      orderFn = orderByRelevance;
    }

    groups.sort(function(group1, group2) {
      var diff = orderFn(group1, group2);
      if (diff == 0) {
        return group1.keys > group2.keys ? 1 : -1;
      } else {
        return diff;
      }
    });
    return groups.filter(this.displayGroup);
  },

  pageGroups: function() {
    var groups = this.displayedGroups();
    var start = this.startGroup();
    return groups.slice(start, start + this.groupsPerPage());
  },

  cols: function() {
    var target = this.target();
    var tables = TableManager.getTables();
    if (target in tables) {
      var table = tables[target];
      return table.cols;
    } else {
      return [];
    }
  },

  pageGroupComponents: function() {
    var target = this.props.target;
    var cols = this.cols();
    var query = this.props.query;
    return this.pageGroups().map(function(group) {
      var key = group.keys.join(",");
      return (
        <ResultGroup
          key={key}
          group={group}
          cols={cols}
          target={target}
          query={query}/>
        );
    });
  },

  addHead: function() {
    var target = this.target();
    if (target == null) {
      return null;
    } else {
      return <th>Add to {target}</th>;
    }
  },

  colHeads: function() {
    var target = this.target();
    var tables = TableManager.getTables();
    if (target in tables) {
      var table = tables[target];
      var makeHead = function(col, i) {
        var key = 'head' + i;
        return <th key={key}>{col}</th>;
      };
      return table.cols.map(makeHead);
    } else {
      return null;
    }
  },

  renderTable: function() {
    var self = this;
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

    // figure out sort order options
    var currentSortOrder = this.state.orderBy;
    var sortOptions = ["count", "relevance"].map(function(sortOption) {
      return (
        <MenuItem
          key={sortOption}
          eventKey={sortOption}
          active={sortOption === currentSortOrder}
          onSelect={function() {
            self.setState({orderBy: sortOption});
          }}>
          Order by {sortOption}
        </MenuItem>);
    });
    var sortOrderDropdownTitle = "Order by " + currentSortOrder;

    return (
      <div>
        <Table striped bordered condensed hover>
          <thead>
            <tr>
              {this.addHead()}
              {this.colHeads()}
              <th>Count</th>
              <th>
                Context
                <ButtonToolbar className="order-by-dropdown">
                  <DropdownButton className="order-by-dropdown-entry" pullRight bsStyle='link' title={sortOrderDropdownTitle}>
                    {sortOptions}
                  </DropdownButton>
                </ButtonToolbar>
              </th>
            </tr>
          </thead>
          <tbody>
            {this.pageGroupComponents()}
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
        {this.props.results.errorMessage}
      </Panel>
    );
  },

  renderNoGroups: function() {
    var numGroups = this.props.results.groups.length;
    var numDisplayed = this.displayedGroups().length;
    var numHidden = numGroups - numDisplayed;
    return (
      <Panel header="Empty Result Set" bsStyle="warning">
        No results to display ({numHidden} hidden).
      </Panel>
    );
  },

  renderPending: function() {
    return <div>Loading...</div>;
  },

  render: function() {
    var results = this.props.results;
    if (results.request == null) {
      return this.renderBlank();
    } else if (results.pending) {
      return this.renderPending();
    } else if (results.errorMessage != null) {
      return this.renderErrorMessage();
    } else if (this.displayedGroups().length == 0) {
      return this.renderNoGroups();
    } else {
      return this.renderTable();
    }
  }
});

module.exports = SearchResults;
