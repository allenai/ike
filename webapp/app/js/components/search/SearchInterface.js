var React = require('react/addons');
var bs = require('react-bootstrap');
var SearchForm = require('./SearchForm.js');
var QueryViewer = require('./QueryViewer.js');
var SearchResults = require('./SearchResults.js');
var TableManager = require('../../managers/TableManager.js');
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
      query: null,
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
      tables: TableManager.getTables(),
      target: this.props.target.value
    };
  },
  makeUri: function(endpoint) {
    var uri = '/api/' + endpoint + '?corpora=';
    var selectedCorpora = this.props.corpora.value.filter(function(corpus) {
      return corpus.selected;
    });
    selectedCorpora.forEach(function(corpus, index) {
      if (selectedCorpora.length > 1 && index > 0) {
        uri += '+';
      }
      if (corpus.selected) {
        uri += corpus.name;
      }
    });
    return uri;
  },
  makeRequestData: function(queryValue) {
    var query = this.makeQuery(queryValue);
    var uri = this.makeUri('groupedSearch');
    return {
      body: JSON.stringify(query),
      uri: uri,
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
    results.value.groups = response.groups;
    this.props.results.value.errorMessage = null;
    results.requestChange(results.value);
    this.refs.searchResults.pageTo(0)
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
  clearGroups: function() {
    var results = this.props.results;
    results.value.groups = [];
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
    this.clearGroups();
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
      requestChange: function(updateValue, callback) {
        var update = {};
        update[name] = updateValue;
        this.setState(update, callback)
      }.bind(this)
    };
  },
  render: function() {
    var query = this.linkState('query');
    var target = this.props.target;
    var config = this.props.config;
    var corpora = this.props.corpora;
    var results = this.props.results;
    var handleSubmit = this.handleSubmit;
    var handleChange = this.search;
    var toggleCorpora = this.props.toggleCorpora;
    var makeUri = this.makeUri;
    var qexpr = this.linkStateCallback('qexpr');
    var form = 
      <SearchForm
        handleSubmit={handleSubmit}
        target={target}
        config={config}
        query={query}
        corpora={corpora}
        toggleCorpora={toggleCorpora}
        makeUri={makeUri}/>;
    var queryViewer =
      <QueryViewer
        target={target}
        config={config}
        makeUri={makeUri}
        handleChange={handleChange}
        rootState={qexpr}/>;
    // Keep a ref to this component so we can reset the page number when a search completes
    var searchResults =
      <SearchResults
        ref="searchResults"
        target={target}
        query={query}
        results={results}
        config={config}/>;
    return (
      <div>
        {form}
        <Row>
          <Col xs={5}>{queryViewer}</Col>
          <Col xs={7}>{searchResults}</Col>
        </Row>
      </div>
    );
  }
});
module.exports = SearchInterface;
