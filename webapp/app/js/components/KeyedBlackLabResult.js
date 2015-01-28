var React = require('react');
var bs = require('react-bootstrap');
var WordDataSeq = require('./WordDataSeq.js');
var KeyedBlackLabResult = React.createClass({
  render: function() {
    var keyedResult = this.props.keyedResult;
    var keyInterval = keyedResult.key;
    var result = keyedResult.result;
    var wordData = function(key, start, end, hl) {
      var data = result.wordData.slice(start, end);
      return <WordDataSeq key={key} data={data} highlighted={hl}/>;
    };
    var keyStart = keyInterval[0];
    var keyEnd = keyInterval[1];
    var n = result.wordData.length;
    var left = wordData("left", 0, keyStart, false);
    var middle = wordData("middle", keyStart, keyEnd, true);
    var right = wordData("right", keyEnd, n, false);
    return (
      <tr>
        <td className="leftContext">{left}</td>
        <td className="hit">{middle}</td>
        <td className="rightContext">{right}</td>
      </tr>
    );
  }
});
module.exports = KeyedBlackLabResult;
