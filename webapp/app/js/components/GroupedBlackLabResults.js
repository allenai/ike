var React = require('react');
var bs = require('react-bootstrap');
var Table = bs.Table;
var KeyedBlackLabResults = require('./KeyedBlackLabResults.js');

var GroupedBlackLabResult = React.createClass({
  render: function() {
    var result = this.props.result;
    return (
      <tr>
        <td>{result.key}</td>
        <td>{result.group.length}</td>
        <td>
          <KeyedBlackLabResults keyedResults={result.group}/>
        </td>
      </tr>
    );
  }
});

var GroupedBlackLabResults = React.createClass({
  render: function() {
    var results = this.props.results;
    results.sort(function(r1, r2) {
      return r2.group.length - r1.group.length;
    });
    var makeRow = function(r) {
      return <GroupedBlackLabResult key={r.key} result={r}/>;
    };
    return (
      <Table striped bordered condensed hover>
        <thead>
          <tr>
            <th>Group</th>
            <th># Hits</th>
            <th>Hits</th>
          </tr>
        </thead>
        <tbody>
          {results.map(makeRow)}
        </tbody>
      </Table>
    );
  }
});
module.exports = GroupedBlackLabResults;
