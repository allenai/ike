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
var Glyphicon = bs.Glyphicon;
const CorporaStore = require('../../stores/CorporaStore.js');
const AuthStore = require('../../stores/AuthStore.js');

var SearchInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    config: React.PropTypes.object.isRequired,
    target: React.PropTypes.object,
    showQueryViewer: React.PropTypes.bool,
    showQuerySuggestions: React.PropTypes.bool,
    queryLink: React.PropTypes.object, // If present, is used to store the query. Otherwise,
                                       // SearchInterface uses its own state to store the query.
    buttonAfterQuery: React.PropTypes.element
  },

  queryLink: function() {
    if(this.props.queryLink)
      return this.props.queryLink;
    else
      return this.linkState('query');
  },

  target: function() {
    if(!this.props.target)
      return null;
    else
      return this.props.target.value;
  },

  showQueryViewer: function() {
    return this.props.showQueryViewer === undefined || this.props.showQueryViewer;
  },

  getInitialState: function() {
    return {
      query: null,
      qexpr: null,
      corpora: CorporaStore.getCorpora(),
      selectedCorpusNames: CorporaStore.getCorpusNames(),
      results: {
        groups: [],
        qexpr: null,
        pending: false,
        request: null,
        errorMessage: null
      }
    };
  },

  componentDidMount() {
    CorporaStore.addChangeListener(this.corporaChanged);

    if(this.queryLink().value)
      this.search();
  },

  componentWillUnmount: function() {
    CorporaStore.removeChangeListener(this.corporaChanged);

    if (this.hasPendingRequest())
      this.cancelRequest();
  },

  corporaChanged: function() {
    this.setState({
      corpora: CorporaStore.getCorpora(),
      selectedCorpusNames: CorporaStore.getCorpusNames()
    });
  },

  makeQuery: function(queryValue) {
    var config = this.props.config.value;
    return {
      query: queryValue,
      config: {
        limit: config.limit,
        evidenceLimit: config.evidenceLimit
      },
      userEmail: AuthStore.getUserEmail(),
      target: this.target()
    };
  },

  makeUri: function(endpoint) {
    return '/api/' + endpoint + '?corpora=' +
      this.state.selectedCorpusNames.join('+');
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
    if (resp.statusCode == 200) {
      var response = JSON.parse(body);
      this.searchSuccess(response);
    } else {
      this.searchFailure(resp.body);
    }
  },

  searchSuccess: function(response) {
    var results = this.state.results;
    results.pending = false;
    results.groups = response.groups;
    results.errorMessage = null;
    this.setState({
      results: results,
      qexpr: response.qexpr
    });
    this.refs.searchResults.pageTo(0);
  },

  searchFailure: function(message) {
    var results = this.state.results;
    results.pending = false;
    results.errorMessage = message;
    this.setState({results: results});
  },

  hasPendingRequest: function() {
    return this.state.results.pending;
  },

  cancelRequest: function() {
    var results = this.state.results;
    results.request.abort();
    results.request = null;
    results.pending = false;
    this.setState({results: results});
  },

  clearGroups: function() {
    var results = this.state.results;
    results.groups = [];
    this.setState({results: results});
  },

  clearQuery: function() {
    this.queryLink().requestChange('');
  },

  clearQueryViewer: function(callback) {
    this.setState({qexpr: null}, callback);
  },

  search: function() {
    if (this.hasPendingRequest())
      this.cancelRequest();
    this.clearGroups();
    var queryValue;
    if (this.state.qexpr == null) {
      queryValue = this.queryLink().value;
    } else {
      queryValue = this.state.qexpr;
    }
    var requestData = this.makeRequestData(queryValue);
    var request = xhr(requestData, this.searchCallback);
    var results = this.state.results;
    results.request = request;
    results.pending = true;
    this.setState({results: results});
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
    var query = this.queryLink();
    var target = this.props.target;
    var config = this.props.config;
    var makeUri = this.makeUri;
    var qexpr = this.linkStateCallback('qexpr');
    var form = 
      <SearchForm
        config={config}
        corpora={this.state.corpora}
        selectedCorpusNames={this.linkState('selectedCorpusNames')}
        handleSubmit={this.handleSubmit}
        makeUri={makeUri}
        query={query}
        target={target}
        buttonAfterQuery={this.props.buttonAfterQuery}
        showQuerySuggestions={this.props.showQuerySuggestions} />;
    // Keep a ref to this component so we can reset the page number when a search completes
    var searchResults =
      <SearchResults
        ref="searchResults"
        target={target}
        query={query}
        results={this.state.results}
        config={config}/>;

    if(this.showQueryViewer()) {
      var queryViewer =
        <QueryViewer
          config={config}
          makeUri={makeUri}
          handleChange={this.search}
          rootState={qexpr}/>;

      return (<div>
        {form}
        <Row>
          <Col xs={5}>{queryViewer}</Col>
          <Col xs={7}>{searchResults}</Col>
        </Row>
      </div>);
    } else {
      return <div>{form} {searchResults}</div>
    }
  }
});
module.exports = SearchInterface;
