var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var QExpr = require('./QExpr.js');
var Well = bs.Well;
var Panel = bs.Panel;
var Button = bs.Button;
var QueryViewer = React.createClass({
  render: function() {
    var rootState = this.props.rootState;
    var handleChange = this.props.handleChange;
    if (rootState == null || rootState.value == null) {
      return <div/>;
    } else {
      return (
        <Panel header="Query Expression Editor">
          <div className="tree" style={{display: 'table', margin: '0 auto'}}>
            <ul>
              <li><QExpr qexpr={rootState.value} rootState={rootState} handleChange={handleChange}/></li>
            </ul>
          </div>
        </Panel>
      );
    }
  }
});
module.exports = QueryViewer;
