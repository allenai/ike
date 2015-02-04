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
      query: 'JJ parsing',
      qexpr: null
    };
  },
  makeQuery: function(queryValue) {
    var config = this.props.config.value;
    return {
      query: queryValue,
      config: {
        limit: config.limit,
        evidenceLimit: config.evidenceLimit
      },
      dictionaries: this.props.dicts.value
    };
  },
  makeRequestData: function(queryValue) {
    var query = this.makeQuery(queryValue);
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
      var response = JSON.parse(body);
      this.searchSuccess(response);
    } else {
      this.searchFailure(resp.body);
    }
  },
  searchSuccess: function(response) {
    var results = this.props.results;
    results.value.rows = response.rows;
    this.props.results.value.errorMessage = null;
    results.requestChange(results.value);
    this.setState({qexpr: response.qexpr});
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
  clearQueryViewer: function(callback) {
    this.setState({qexpr: null}, callback);
  },
  search: function() {
    if (this.hasPendingRequest()) {
      this.cancelRequest();
    }
    this.clearRows();
    var queryValue;
    if (this.state.qexpr == null) {
      queryValue = this.state.query;
    } else {
      queryValue = this.state.qexpr;
    }
    var requestData = this.makeRequestData(queryValue);
    var request = xhr(requestData, this.searchCallback);
    var results = this.props.results;
    results.value.request = request;
    results.value.pending = true;
    results.requestChange(results.value);
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.clearQueryViewer(this.search);
  },
  linkStateCallback: function(name) {
    return {
      value: this.state[name],
      requestChange: function(update, callback) {
        this.setState({name: update}, callback)
      }.bind(this)
    };
  },
  render: function() {
    var query = this.linkState('query');
    var target = this.props.target;
    var dicts = this.props.dicts;
    var config = this.props.config;
    var results = this.props.results;
    var handleSubmit = this.handleSubmit;
    var handleChange = this.search;
    var qexpr = this.linkStateCallback('qexpr');
    var form = 
      <SearchForm
        handleSubmit={handleSubmit}
        target={target}
        dicts={dicts}
        query={query}/>;
    var queryViewer =
      <QueryViewer
        target={target}
        dicts={dicts}
        handleChange={handleChange}
        rootState={qexpr}/>;
    var searchResults =
      <SearchResults
        key={results.value.rows}
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
