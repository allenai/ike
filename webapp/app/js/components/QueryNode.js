var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var WordNode = require('./WordNode.js');
var ClusterNode = require('./ClusterNode.js');
var DictNode = require('./DictNode.js');
var POSNode = require('./POSNode.js');
var Glyphicon = bs.Glyphicon;
var QueryNode = React.createClass({
  nodeNames: {
    'QWord': 'Word',
    'QDict': 'Dict',
    'QPlus': <Glyphicon glyph="plus"/>,
    'QStar': <span>&#10033;</span>,
    'QPos': 'POS',
    'QCluster': 'Cluster',
    'QSeq': 'Sequence',
    'QWildcard$': '.',
    'QNamed': 'Named Capture',
    'QUnnamed': 'Unnamed Capture',
    'QNonCap': 'Non-Capturing',
    'QDisj': 'Or'
  },
  leafNodes: {
    'QWord': true,
    'QDict': true,
    'QCluster': true,
    'QPos': true
  },
  getNodeName: function(name) {
    if (name in this.nodeNames) {
      return this.nodeNames[name];
    } else {
      return name;
    }
  },
  getDefaultProps: function() {
    return {
      path: '0'
    };
  },
  leafContent: function(node) {
    switch (node.name) {
      case "QWord":
        return <WordNode node={node}/>;
      case "QDict":
        return <DictNode node={node}/>;
      case "QPos":
        return <POSNode node={node}/>;
      case "QCluster":
        return <ClusterNode node={node}/>;
      default:
        return null;
    }
  },
  nodeContent: function(node) {
    var children = this.childrenQueryNodes();
    return (
      <div>
        <div>{this.getNodeName(node.name)}</div>
        {children}
      </div>
    );
  },
  content: function(node) {
    if (node.name in this.leafNodes) {
      return this.leafContent(node);
    } else {
      return this.nodeContent(node);
    }
  },
  childrenQueryNodes: function() {
    var children = this.props.node.children;
    var thisPath = this.props.path;
    var makeNode = function(child, i) {
      var path = thisPath + '.' + i;
      return <li key={path}><QueryNode path={path} node={child}/></li>;
    };
    if (children.length > 0) {
      return (
        <ul>
          {children.map(makeNode)}
        </ul>
      );
    } else {
      return null;
    }
  },
  render: function() {
    return this.content(this.props.node);
  }
});
module.exports = QueryNode;
