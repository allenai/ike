var React = require('react');
var QExpr = require('./QExpr.js');
var QSeq = React.createClass({
  render: function() {
    var qexpr = this.props.qexpr;
/*    var children = qexpr.qexprs.map(function(q, i) {
      return <li key={i}><QExpr qexpr={q}/></li>;
    });*/
    return (
      <div>
        Sequence
        {qexpr.qexprs.map(function(q,i) { return <b>{q.type} </b>; })}
      </div>
    );
  }
});
module.exports = QSeq;
