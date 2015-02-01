var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Navbar = bs.Navbar;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Input = bs.Input;
var Glyphicon = bs.Glyphicon;
var SearchInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      query: '',
      limit: 1000,
      evidenceLimit: 1,
      request: null,
      errorMessage: null
    };
  },
  makeQuery: function() {
    return {
      query: this.state.query,
      limit: this.state.limit,
      evidenceLimit: this.state.evidenceLimit
    };
  },
  makeRequestData: function() {
    var query = this.makeQuery();
    return {
      body: JSON.stringify(query),
      uri: '/api/search',
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
  },
  searchCallback: function(err, resp, body) {
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
    results.requestChange(results.value);
  },
  searchFailure: function(message) {
    this.setState({errorMessage: message});
  },
  hasPendingRequest: function() {
    return this.state.request != null;
  },
  cancelRequest: function() {
    this.state.request.abort();
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
    this.setState({request: request});
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.clearQuery();
    this.search();
  },
  render: function() {
    return (
      <Navbar fluid>
        <form className="searchForm" onSubmit={this.handleSubmit}>
          <Input
            type="text"
            placeholder="Enter Query"
            valueLink={this.linkState('query')}/>
        </form>
      </Navbar>
    );
  }
});
module.exports = SearchInterface;
