var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var QueryNode = require('./QueryNode.js');
var Well = bs.Well;
var QueryViewer = React.createClass({
  getInitialState: function() {
    return {
      request: null,
      pending: true,
      parseResults: null,
      errorMessage: null
    }
  },
  makeRequestData: function() {
    return {
      body: JSON.stringify({query: this.props.query}),
      uri: '/api/parse',
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
  },
  parseCallback: function(err, resp, body) {
    if (resp.statusCode == 200) {
      this.setState({
        parseResults: JSON.parse(body),
        errorMessage: null,
        pending: false
      });
    } else {
      this.setState({
        parseResults: null,
        errorMessage: resp.body,
        pending: false
      });
    }
  },
  parse: function() {
    var requestData = this.makeRequestData();
    var request = xhr(requestData, this.parseCallback);
    this.setState({pending: true, request: request});
  },
  componentWillMount: function() {
    this.parse();
  },
  render: function() {
    var content;
    if (this.state.pending) {
      content = <div>Loading...</div>;
    } else {
      content = <div className="tree"><QueryNode node={this.state.parseResults}/></div>;
    }
    return (
      <Well>{content}</Well>
    );
  }
});
module.exports = QueryViewer;
