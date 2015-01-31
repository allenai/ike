var React = require('react');
var bs = require('react-bootstrap');
var QueryNode = require('./QueryNode.js');
var QueryVisualization = React.createClass({
  render: function() {
    var node = this.props.node;
    return (
      <div className="tree">
        <ul>
          <li>
            <QueryNode node={node}/>
          </li>
        </ul>
      </div>
    );
  }
});
module.exports = QueryVisualization;
