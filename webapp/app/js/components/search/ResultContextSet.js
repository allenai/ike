var React = require('react');
var bs = require('react-bootstrap');
var ResultContext = require('./ResultContext.js');
var ResultContextSet = React.createClass({
  render: function() {
    var row = this.props.row;
    var group = row.group;
    var contexts = group.map(function(context, i) {
      return <div key={i}><ResultContext context={context}/></div>;
    });
    return <div>{contexts}</div>;
  }
});
module.exports = ResultContextSet;
