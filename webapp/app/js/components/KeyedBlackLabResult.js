var React = require('react');
var bs = require('react-bootstrap');
var WordDataSeq = require('./WordDataSeq.js');
var KeyedBlackLabResult = React.createClass({
  render: function() {
    var keyedResult = this.props.keyedResult;
    var keyInterval = keyedResult.key;
    var result = keyedResult.result;
    var wordData = function(key, start, end) {
      var data = result.wordData.slice(start, end);
      return <WordDataSeq key={key} data={data}/>;
    };
    var keyStart = keyInterval[0];
    var keyEnd = keyInterval[1];
    var n = result.wordData.length;
    var left = wordData("left", 0, keyStart);
    var middle = wordData("middle", keyStart, keyEnd);
    var right = wordData("right", keyEnd, n);
    return (
      <tr>
        <td style={{width: "30%"}}>{left}</td>
        <td>{middle}</td>
        <td style={{width: "30%"}}>{right}</td>
      </tr>
    );
  }
});
module.exports = KeyedBlackLabResult;
