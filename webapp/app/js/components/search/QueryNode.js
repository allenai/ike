var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var QueryNode = React.createClass({
  nodeNames: {
    'QWord': 'Word',
    'QDict': 'Dict',
    'QPlus': <Glyphicon glyph="plus"/>,
    'QStar': <Glyphicon glyph="asterisk"/>,
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
  content: function(node) {
    var children = this.childrenQueryNodes();
    return (
      <div>
        <div>{this.getNodeName(node.name)}</div>
        {children}
      </div>
    );
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
