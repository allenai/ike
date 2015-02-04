var React = require('react');
var bs = require('react-bootstrap');
var Glyphicon = bs.Glyphicon;
var Button = bs.Button;

var QExprMixin = {
  replaceExprAtPath: function(replacement, callback, path, pointer) {
    var linkedState = this.props.linkedState;
    if (path == null) {
      path = this.props.path;
    }
    if (pointer == null) {
      pointer = linkedState.value;
    }
    if (path.length == 0) {
      console.error('Could not update expr at empty path');
    } else if (path.length == 1) {
      pointer[path[0]] = replacement;
      linkedState.requestChange(linkedState.value, callback);
    } else {
      pointer = pointer[path[0]];
      this.replaceExprAtPath(replacement, callback, path.slice(1), pointer);
    }
  },
  updateSelf: function(replacement) {
    var linkedState = this.props.linkedState;
    var callback = this.props.handleChange;
    this.replaceExprAtPath(replacement, callback);
  }
}

var QSeq = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var qexpr = this.props.qexpr;
    var path = this.props.path;
    var linkedState = this.props.linkedState;
    var handleChange = this.props.handleChange;
    var children = qexpr.qexprs.map(function(q, i) {
      var childPath = path.slice();
      childPath.push('qexprs');
      childPath.push(i);
      return <li key={i}><QExpr qexpr={q} path={childPath} linkedState={linkedState} handleChange={handleChange}/></li>;
    });
    return <div>Sequence<ul>{children}</ul></div>;
  }
});
var QDisj = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var qexpr = this.props.qexpr;
    var path = this.props.path;
    var linkedState = this.props.linkedState;
    var handleChange = this.props.handleChange;
    var children = qexpr.qexprs.map(function(q, i) {
      var childPath = path.slice();
      childPath.push('qexprs');
      childPath.push(i);
      return <li key={i}><QExpr qexpr={q} path={childPath} linkedState={linkedState} handleChange={handleChange}/></li>;
    });
    return <div>Or<ul>{children}</ul></div>;
  }
});
var QPos = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>POS<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QWord = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var path = this.props.path;
    var qexpr = this.props.qexpr;
    var handleChange = this.props.handleChange;
    var reverse = function() {
      qexpr.value = qexpr.value.split("").reverse().join("");
      this.updateSelf(qexpr);
    }.bind(this);
    var button = <Button onClick={reverse}>Click!</Button>;
    return <div>Word<ul><li>{this.props.qexpr.value} {button}</li></ul></div>;
  }
});
var QDict = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Dict<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QCluster = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Cluster<ul><li>{this.props.qexpr.value}</li></ul></div>;
  }
});
var QWildcard = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Wildcard</div>;
  }
});
var QNamed = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    var handleChange = this.props.handleChange;
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState} handleChange={handleChange}/>;
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
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    var handleChange = this.props.handleChange;
    childPath.push('qexpr');
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState} handleChange={handleChange}/>;
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
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var handleChange = this.props.handleChange;
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState} handleChange={handleChange}/>;
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
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var handleChange = this.props.handleChange;
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState} handleChange={handleChange}/>;
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
  mixins: [QExprMixin],
  render: function() {
    var linkedState = this.props.linkedState;
    var childPath = this.props.path.slice();
    childPath.push('qexpr');
    var handleChange = this.props.handleChange;
    var child = <QExpr qexpr={this.props.qexpr.qexpr} path={childPath} linkedState={linkedState} handleChange={handleChange}/>;
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
    var handleChange = this.props.handleChange;
    if (type in this.qexprComponents) {
      var component = this.qexprComponents[type];
      var path;
      if (this.props.path == null) {
        path = [];
      } else {
        path = this.props.path;
      }
      return React.createElement(component, {qexpr: qexpr, path: path, linkedState: linkedState, handleChange: handleChange});
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
