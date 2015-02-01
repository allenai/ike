var React = require('react/addons');
var bs = require('react-bootstrap');
var WordDataSeq = require('./WordDataSeq.js');
var KeyedBlackLabResult = React.createClass({
  render: function() {
    var keyedResult = this.props.keyedResult;
    var keyInterval = keyedResult.key;
    var result = keyedResult.result;
    var wordData = function(key, start, end, hl) {
      var data = result.wordData.slice(start, end);
      var words = data.map(function(d) { return d.word; });
      var cx = React.addons.classSet;
      var classes = cx({
        'highlighted': hl
      });
      return <span className={classes}>{words.join(" ")}</span>;
    };
    var keyStart = keyInterval[0];
    var keyEnd = keyInterval[1];
    var n = result.wordData.length;
    var left = wordData("left", 0, keyStart, false);
    var middle = wordData("middle", keyStart, keyEnd, true);
    var right = wordData("right", keyEnd, n, false);
    return (
      <div>{left} {middle} {right}</div>
    );
  }
});
module.exports = KeyedBlackLabResult;
