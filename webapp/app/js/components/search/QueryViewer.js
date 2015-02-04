var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var QExpr = require('./QExpr.js');
var Well = bs.Well;
var Panel = bs.Panel;
var QueryViewer = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
    if (qexpr.value == null) {
      return <div/>;
    } else {
      console.log(qexpr);
      return (
        <Panel header="Query Expression Editor">
          <div className="tree" style={{display: 'table', margin: '0 auto'}}>
            <ul>
              <li><QExpr qexpr={qexpr.value} linkedState={qexpr}/></li>
            </ul>
          </div>
        </Panel>
      );
    }
  }
});
module.exports = QueryViewer;
