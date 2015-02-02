var React = require('react');
var bs = require('react-bootstrap');
var Glyphicon = bs.Glyphicon;
var QSeq = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
    var children = qexpr.qexprs.map(function(q, i) {
      return <li key={i}><QExpr qexpr={q}/></li>;
    });
    return <div>Sequence<ul>{children}</ul></div>;
  }
});
var QDisj = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
    var children = qexpr.qexprs.map(function(q, i) {
      return <li key={i}><QExpr qexpr={q}/></li>;
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
    return <div>Word<ul><li>{this.props.qexpr.value}</li></ul></div>;
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
    var child = <QExpr qexpr={this.props.qexpr.qexpr}/>;
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
    var child = <QExpr qexpr={this.props.qexpr.qexpr}/>;
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
    var child = <QExpr qexpr={this.props.qexpr.qexpr}/>;
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
    var child = <QExpr qexpr={this.props.qexpr.qexpr}/>;
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
    var child = <QExpr qexpr={this.props.qexpr.qexpr}/>;
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
    var qexpr = this.props.qexpr;
    var type = qexpr.type;
    if (type in this.qexprComponents) {
      var component = this.qexprComponents[type];
      return React.createElement(component, {qexpr: qexpr});
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
