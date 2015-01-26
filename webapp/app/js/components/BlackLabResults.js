var React = require('react');
var BlackLabResult = require('./BlackLabResult.js');
var Table = require('react-bootstrap').Table;

var BlackLabResults = React.createClass({
  groupNames: function() {
    result = {};
    this.props.results.map(function(r) {
      Object.keys(r.captureGroups).map(function(n) {
        result[n] = true;
      });
    });
    return Object.keys(result);
  },
  render: function() {
    var results = this.props.results;
    var groupNames = this.groupNames();
    var createResult = function(result, i) {
      return <BlackLabResult result={result} key={result.id} groupNames={groupNames} />;
    };
    var createHeaderColumn = function(name) {
      return <th key={name}>{name}</th>;
    };
    var headerColumns = groupNames.map(createHeaderColumn);
    return (
      <Table striped bordered condensed hover> 
        <thead>
          <tr>
            {headerColumns}
            <th>Context</th>
          </tr>
        </thead>
        <tbody>
          {results.map(createResult)}
        </tbody>
      </Table>
    );
  }
});
module.exports = BlackLabResults;
