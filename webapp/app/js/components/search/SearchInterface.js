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
    buttonAfterQuery: React.PropTypes.element,
    tag: React.PropTypes.string // This is a weird property. Whenever it changes, the search form
                                // triggers the search without anyone having to hit enter.
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

  getInitialResults: function() {
    return {
      groups: [],
      query: null,
      qexpr: null,  // do we need this?
      pending: false,
      request: null,
      errorMessage: null
    };
  },

  getInitialState: function() {
    return {
      query: null,
      qexpr: null,
      corpora: CorporaStore.getCorpora(),
      selectedCorpusNames: CorporaStore.getCorpusNames(),
      results: this.getInitialResults()
    };
  },

  componentDidUpdate: function(prevProps, prevState) {
    // if the tag changed, search again
    if(prevProps.tag !== this.props.tag) {
      var self = this;
      this.setState({qexpr: null}, self.search);
      return;
    }

    // returns the number of columns for a given target (used below)
    const columnCountForTarget = function(target) {
      if(!target)
        return null;

      const targetValue = target.value;
      if(!targetValue)
        return null;

      const table = TableManager.getTables()[targetValue];
      if(!table)
        return null;

      return table.cols.length;
    };

    // if the target changed, wipe results if the columns don't match
    if(columnCountForTarget(this.props.target) !== columnCountForTarget(prevProps.target)) {
      this.clearGroups();
      this.clearQueryViewer();
    }
  },

  componentDidMount: function() {
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
    var results = this.getInitialResults();
    results.groups = response.groups;
    results.query = this.state.results.query;
    results.qexpr = response.qexpr;
    this.setState({
      results: results,
      qexpr: response.qexpr
    });
    this.refs.searchResults.pageTo(0);
  },

  searchFailure: function(message) {
    var results = this.getInitialResults();
    results.errorMessage = message;
    this.setState({results: results});
  },

  hasPendingRequest: function() {
    return this.state.results.pending;
  },

  cancelRequest: function() {
    this.state.results.request.abort();
    this.setState({results: this.getInitialResults()});
  },

  clearGroups: function() {
    var results = this.getInitialResults();
    results.pending = this.state.results.pending;
    results.request = this.state.results.request;
    results.errorMessage = this.state.results.errorMessage;
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
    if(!queryValue)
      return;
    var requestData = this.makeRequestData(queryValue);
    var request = xhr(requestData, this.searchCallback);
    var results = this.getInitialResults();
    results.request = request;
    results.query = queryValue;
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
