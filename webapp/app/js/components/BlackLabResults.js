var React = require('react');
var BlackLabResult = require('./BlackLabResult.js');
var BlackLabResults = React.createClass({
  render: function() {
    var results = this.props.results;
    var createResult = function(result, i) {
      return <BlackLabResult result={result} key={result.id}/>;
    };
    return (
      <table className="blackLabResults">
        {results.map(createResult)}
      </table>
    );
  }
});
module.exports = BlackLabResults;
