var React = require('react');
var bs = require('react-bootstrap');
var ResultContext = React.createClass({
  render: function() {
    var context = this.props.context;
    var start = context.key[0];
    var end = context.key[1];
    var wordData = context.result.wordData;
    var words = wordData.map(function(d) { return d.word; });
    var numWords = words.length;
    var leftWords = words.slice(0, start).join(" ");
    var middleWords = words.slice(start, end).join(" ");
    var rightWords = words.slice(end, numWords).join(" ");
    return (
      <div>
        <span>{leftWords}</span>
        {" "}
        <span className='highlighted'>{middleWords}</span>
        {" "}
        <span>{rightWords}</span>
      </div>
    );
  }
});
module.exports = ResultContext;
