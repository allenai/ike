var React = require('react');
var bs = require('react-bootstrap');
var ResultContext = React.createClass({
  render: function() {
    var context = this.props.context;
    var words = context.result.wordData.map(function(w) { return w.word });
    var spans = context.keys;
    var highlightedIndex = function(i) {
      for (var k = 0; k < spans.length; k++) {
        var span = spans[k];
        if (span[0] <= i && i < span[1]) {
          return true;
        }
      }
      return false;
    };
    var highlighted = words.map(function(word, i) {
      if (highlightedIndex(i)) {
        return <span key={i} className='highlighted'>{word} </span>
      } else {
        return <span key={i}>{word} </span>;
      }
    });
    return <div>{highlighted}</div>;
  }
});
module.exports = ResultContext;
