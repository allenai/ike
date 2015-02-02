var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Navbar = bs.Navbar;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Button = bs.Button;
var Input = bs.Input;
var InputGroup = bs.InputGroup;
var Glyphicon = bs.Glyphicon;
var SearchInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      query: '',
      request: null,
      errorMessage: null
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
    var a = <a href="#">Search Options</a>;
    return (
      <div>
        <form onSubmit={this.handleSubmit}>
          <Input
            type="text"
            placeholder="Enter Query"
            valueLink={this.linkState('query')}/>
        </form>
      </div>
    );
  }
});
module.exports = SearchInterface;
