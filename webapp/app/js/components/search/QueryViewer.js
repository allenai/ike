var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var tree = require('./Tree.js');
var Tree = tree.Tree;
var Node = tree.Node;
var QExpr = require('./QExpr.js');
var Well = bs.Well;
var Panel = bs.Panel;
var Accordion = bs.Accordion;
var Button = bs.Button;
var QueryViewer = React.createClass({
  render: function() {
    var rootState = this.props.rootState;
    var handleChange = this.props.handleChange;
    var config = this.props.config;
    if (rootState == null || rootState.value == null) {
      return <div/>;
    } else {
      return (
        <Panel header="Query Expression Editor">
          <div className="queryViewer" style={{'font-size': 'small', display: 'table', margin: '0 auto'}}>
            <Tree>
              <Node>
                <QExpr
                  config={config}
                  qexpr={rootState.value}
                  rootState={rootState}
                  handleChange={handleChange}/>
              </Node>
            </Tree>
          </div>
        </Panel>
      );
    }
  }
});
module.exports = QueryViewer;
