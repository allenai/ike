var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var QExpr = require('./QExpr.js');
var Well = bs.Well;
var Panel = bs.Panel;
var QueryViewer = React.createClass({
  render: function() {
    var qexpr = this.props.results.value.qexpr;
    if (qexpr == null) {
      return <div/>;
    } else {
      return (
        <Panel header="Query Expression Editor">
          <div className="tree" style={{display: 'table', margin: '0 auto'}}>
            <ul>
              <li><QExpr qexpr={qexpr}/></li>
            </ul>
          </div>
        </Panel>
      );
    }
  }
});
module.exports = QueryViewer;
