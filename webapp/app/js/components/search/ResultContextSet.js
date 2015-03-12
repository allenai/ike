var React = require('react');
var bs = require('react-bootstrap');
var ResultContext = require('./ResultContext.js');
var ResultContextSet = React.createClass({
  render: function() {
    var group = this.props.group;
    var results = group.results;
    var contexts = results.map(function(context, i) {
      return <div key={i}><ResultContext context={context}/></div>;
    });
    return <div>{contexts}</div>;
  }
});
module.exports = ResultContextSet;
