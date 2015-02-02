var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var QExpr = require('./qexpr/QExpr.js');
var Well = bs.Well;
var QueryViewer = React.createClass({
  render: function() {
    var qexpr = this.props.results.value.qexpr;
    if (qexpr == null) {
      return <div/>;
    } else {
      return (
        <div className="tree">
          <ul>
            <li><QExpr qexpr={qexpr}/></li>
          </ul>
        </div>
      );
    }
  }
});
module.exports = QueryViewer;
