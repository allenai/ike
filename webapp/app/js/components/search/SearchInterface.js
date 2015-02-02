var React = require('react/addons');
var bs = require('react-bootstrap');
var SearchForm = require('./SearchForm.js');
var QueryViewer = require('./QueryViewer.js');
var SearchResults = require('./SearchResults.js');
var xhr = require('xhr');
var Navbar = bs.Navbar;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Button = bs.Button;
var Input = bs.Input;
var Row = bs.Row;
var Col = bs.Col;
var InputGroup = bs.InputGroup;
var Glyphicon = bs.Glyphicon;
var SearchInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      query: 'JJ parsing'
    };
  },
  makeQuery: function() {
    var config = this.props.config.value;
    return {
      query: this.state.query,
      limit: config.limit,
      evidenceLimit: config.evidenceLimit,
      dictionaries: this.props.dicts.value
    };
  },
  makeRequestData: function() {
    var query = this.makeQuery();
    return {
      body: JSON.stringify(query),
      uri: '/api/groupedSearch',
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
  },
  searchCallback: function(err, resp, body) {
    var results = this.props.results;
    results.value.pending = false;
    results.requestChange(results.value);
    if (resp.statusCode == 200) {
      var rows = JSON.parse(body);
      this.searchSuccess(rows);
    } else {
      this.searchFailure(resp.body);
    }
  },
  searchSuccess: function(rows) {
    var results = this.props.results;
    results.value.rows = rows;
    this.props.results.value.errorMessage = null;
    results.requestChange(results.value);
  },
  searchFailure: function(message) {
    var results = this.props.results;
    results.value.errorMessage = message;
    results.requestChange(results.value);
  },
  hasPendingRequest: function() {
    var results = this.props.results.value;
    return results.pending;
  },
  cancelRequest: function() {
    var results = this.props.results;
    results.value.request.abort();
    results.value.request = null;
    results.value.pending = false;
    results.requestChange(results.value);
  },
  clearRows: function() {
    var results = this.props.results;
    results.value.rows = [];
    results.requestChange(results.value);
  },
  clearQuery: function() {
    this.setState({query: ''});
  },
  search: function() {
    if (this.hasPendingRequest()) {
      this.cancelRequest();
    }
    this.clearRows();
    var requestData = this.makeRequestData();
    var request = xhr(requestData, this.searchCallback);
    var results = this.props.results;
    results.value.request = request;
    results.value.pending = true;
    results.requestChange(results.value);
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.clearQuery();
    this.search();
  },
  render: function() {
    var query = this.linkState('query');
    var target = this.props.target;
    var dicts = this.props.dicts;
    var config = this.props.config;
    var results = this.props.results;
    var handleSubmit = this.handleSubmit;
    var form = 
      <SearchForm
        handleSubmit={handleSubmit}
        target={target}
        dicts={dicts}
        query={query}/>;
    var queryViewer =
      <QueryViewer
        handleSubmit={handleSubmit}
        target={target}
        dicts={dicts}
        query={query}/>;
    var searchResults =
      <SearchResults
        target={target}
        dicts={dicts}
        query={query}
        results={results}
        config={config}/>;
    return (
      <div>
        {form}
        <Row>
          <Col xs={4}>{queryViewer}</Col>
          <Col xs={8}>{searchResults}</Col>
        </Row>
      </div>
    );
  }
});
module.exports = SearchInterface;
