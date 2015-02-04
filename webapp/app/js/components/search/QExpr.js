var React = require('react');
var bs = require('react-bootstrap');
var Glyphicon = bs.Glyphicon;
var Button = bs.Button;
var replaceExprAtPath = function(linkedState, path, replacement, pointer) {
  if (pointer == null) {
    pointer = linkedState.value;
  }
  if (path.length == 0) {
    console.error('Could not update expr at empty path');
  } else if (path.length == 1) {
    pointer[path[0]] = replacement;
    linkedState.requestChange(linkedState.value);
  } else {
    pointer = pointer[path[0]];
    replaceExprAtPath(linkedState, path.slice(1), replacement, pointer);
  }
};
var QSeq = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
    var path = this.props.path;
    var linkedState = this.props.linkedState;
    var children = qexpr.qexprs.map(function(q, i) {
      var childPath = path.slice();
      childPath.push('qexprs');
      childPath.push(i);
      return <li key={i}><QExpr qexpr={q} path={childPath} linkedState={linkedState}/></li>;
    });
    return <div>Sequence<ul>{children}</ul></div>;
  }
});
var QDisj = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
    var path = this.props.path;
    var linkedState = this.props.linkedState;
    var children = qexpr.qexprs.map(function(q, i) {
      var childPath = path.slice();
      childPath.push('qexprs');
      childPath.push(i);
      return <li key={i}><QExpr qexpr={q} path={childPath} linkedState={linkedState}/></li>;
    });
    return <div>Or<ul>{children}</ul></div>;
  }
});
var QPos = React.createClass({
  render: function() {
    return <div>POS<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QWord = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var path = this.props.path;
    var qexpr = this.props.qexpr;
    var reverse = function() {
      qexpr.value = qexpr.value.split("").reverse().join("");
      replaceExprAtPath(linkedState, path, qexpr);
    };
    var button = <Button onClick={reverse}>Click!</Button>;
    return <div>Word<ul><li>{this.props.qexpr.value} {button}</li></ul></div>;
  }
});
var QDict = React.createClass({
  render: function() {
    return <div>Dict<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QCluster = React.createClass({
  render: function() {
    return <div>Cluster<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QWildcard = React.createClass({
  render: function() {
    return <div>Wildcard</div>;
  }
});
var QNamed = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState}/>;
    return (
      <div>
        Capture({this.props.qexpr.name})
        <ul>
          <li>{child}</li>
        </ul>
      </div>
    );
  }
});
var QUnnamed = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState}/>;
    return (
      <div>
        Capture
        <ul>
          <li>{child}</li>
        </ul>
      </div>
    );
  }
});
var QNonCap = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState}/>;
    return (
      <div>
        Non-Capture
        <ul>
          <li>{child}</li>
        </ul>
      </div>
    );
  }
});
var QStar = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState}/>;
    return (
      <div>
        <Glyphicon glyph="asterisk"/>
        <ul>
          <li>{child}</li>
        </ul>
      </div>
    );
  }
});
var QPlus = React.createClass({
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState}/>;
    return (
      <div>
        <Glyphicon glyph="plus"/>
        <ul>
          <li>{child}</li>
        </ul>
      </div>
    );
  }
});
var QExpr = React.createClass({
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
    var linkedState = this.props.linkedState;
    var qexpr = this.props.qexpr;
    var type = qexpr.type;
    if (type in this.qexprComponents) {
      var component = this.qexprComponents[type];
      var path;
      if (this.props.path == null) {
        path = [];
      } else {
        path = this.props.path;
      }
      return React.createElement(component, {qexpr: qexpr, path: path, linkedState: linkedState});
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
