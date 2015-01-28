var React = require('react');
var bs = require('react-bootstrap');
var KeyedBlackLabResult = require('./KeyedBlackLabResult.js');
var Table = bs.Table;
var KeyedBlackLabResults = React.createClass({
  render: function() {
    var keyedResults = this.props.keyedResults;
    var makeRow = function(keyedResult, i) {
      return <KeyedBlackLabResult key={i} keyedResult={keyedResult}/>;
    };
    return (
      <Table striped bordered hover>
        <tbody>
          {keyedResults.map(makeRow)}
        </tbody>
      </Table>
    );
  }
});
module.exports = KeyedBlackLabResults;
