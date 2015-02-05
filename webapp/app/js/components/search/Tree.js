var React = require('react');
var Tree = React.createClass({
  render: function() { return <ul className="tree">{this.props.children}</ul>; }
});
var Node = React.createClass({
  render: function() { return <li className="tree">{this.props.children}</li>; }
});
module.exports = {
  Tree: Tree,
  Node: Node
};
