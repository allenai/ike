var React = require('react');
var bs = require('react-bootstrap');
var Glyphicon = bs.Glyphicon;
var DropdownButton = bs.DropdownButton;
var tree = require('./Tree.js');
var MenuItem = bs.MenuItem;
var Node = tree.Node;
var Tree = tree.Tree;

var QExprMixin = {
  propTypes: {
    rootState: React.PropTypes.shape({
      value: React.PropTypes.object.isRequired,
      requestChange: React.PropTypes.func.isRequired
    }).isRequired,
    qexpr: React.PropTypes.object.isRequired,
    handleChange: React.PropTypes.func.isRequired,
    path: React.PropTypes.arrayOf(React.PropTypes.oneOfType([
      React.PropTypes.string,
      React.PropTypes.number
    ])).isRequired
  },
  replaceExprAtPath: function(replacement, callback, path, pointer) {
    var qexprLink = this.props.qexprLink;
    if (path == null) {
      path = this.props.path;
    }
    if (pointer == null) {
      pointer = qexprLink.value;
    }
    if (path.length == 0) {
      console.error('Could not update expr at empty path');
    } else if (path.length == 1) {
      pointer[path[0]] = replacement;
      qexprLink.requestChange(qexprLink.value, callback);
    } else {
      pointer = pointer[path[0]];
      this.replaceExprAtPath(replacement, callback, path.slice(1), pointer);
    }
  },
  updateSelf: function(replacement) {
    var qexprLink = this.props.qexprLink;
    var callback = this.props.handleChange;
    this.replaceExprAtPath(replacement, callback);
  },
  children: function() {
    var attr = this.pathAttr();
    if (attr == null) {
      return null;
    } else if (attr == 'qexpr') {
      return [this.props.qexpr[attr]];
    } else if (attr == 'qexprs') {
      return this.props.qexpr[attr];
    } else {
      return [];
    }
  },
  pathAttr: function() {
    var qexpr = this.props.qexpr;
    if ('qexpr' in qexpr) {
      return 'qexpr';
    } else if ('qexprs' in qexpr) {
      return 'qexprs';
    } else {
      return null;
    }
  },
  makeChildComponent: function(childExpr, index) {
    var attr = this.pathAttr();
    var childPath = this.props.path.slice();
    var handleChange = this.props.handleChange;
    var rootState = this.props.rootState;
    childPath.push(attr);
    childPath.push(index);
    return (
      <Node key={index}>
        <QExpr
          qexpr={childExpr}
          path={childPath}
          handleChange={handleChange}
          rootState={rootState}/>
      </Node>
    );
  },
  childComponents: function() {
    var children = this.children();
    return children.map(this.makeChildComponent);
  }
}
var InnerNodeMixin = {
  mixins: [QExprMixin],
  render: function() {
    var name;
    if (typeof(this.name) == 'function') {
      name = this.name();
    } else {
      name = this.name;
    }
    return <div>{name}<Tree>{this.childComponents()}</Tree></div>;
  }
};
var QSeq = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Sequence'
});
var QDisj = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Or'
});
var QPos = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>POS<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
  }
});
var QWord = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var value = this.props.qexpr.value;
    var button = (
      <div>
      <DropdownButton bsStyle="link" title={value}>
        <MenuItem eventKey={1}>Do something</MenuItem>
        <MenuItem eventKey={1}><input type="text"/></MenuItem>
      </DropdownButton>
      </div>
    );
    return button;
  }
});
var QDict = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Dict<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
  }
});
var QCluster = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Cluster<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
  }
});
var QWildcard = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Wildcard</div>;
  }
});
var QNamed = React.createClass({
  mixins: [InnerNodeMixin],
  name: function() {
    return 'Capture(' + this.props.qexpr.name + ')';
  }
});
var QUnnamed = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Capture'
});
var QNonCap = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'NonCapture'
});
var QStar = React.createClass({
  mixins: [InnerNodeMixin],
  name: <Glyphicon glyph="asterisk"/>
});
var QPlus = React.createClass({
  mixins: [InnerNodeMixin],
  name: <Glyphicon glyph="plus"/>
});
var QExpr = React.createClass({
  mixins: [QExprMixin],
  getDefaultProps: function() {
    return {
      path: []
    }
  },
  qexprComponents: {
    QWord: QWord,
    QPos: QPos,
    QSeq: QSeq,
    QDict: QDict,
    QCluster: QCluster,
    QWildcard: QWildcard,
    QNamed: QNamed,
    QUnnamed: QUnnamed,
    QNonCap: QNonCap,
    QStar: QStar,
    QPlus: QPlus,
    QDisj: QDisj
  },
  render: function() {
    var qexpr = this.props.qexpr;
    var type = qexpr.type;
    if (type in this.qexprComponents) {
      var component = this.qexprComponents[type];
      var rootState = this.props.rootState;
      var handleChange = this.props.handleChange;
      var path = this.props.path;
      var implProps = {
        qexpr: qexpr,
        path: path,
        rootState: rootState,
        handleChange: handleChange
      };
      return React.createElement(component, implProps);
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
