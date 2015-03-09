var React = require('react');
var bs = require('react-bootstrap');
var AddResultButton = require('./AddResultButton.js');
var ResultContextSet = require('./ResultContextSet.js');
var ResultRow = React.createClass({
  render: function() {
    var row = this.props.row;
    var tables = this.props.tables;
    var target = this.props.target;
    var addCol;
    if (target.value == null) { 
      addCol = null
    } else {
      addCol =
        <td><AddResultButton row={row} target={target} tables={tables}/></td>;
    }
    var context = <ResultContextSet row={row}/>;
    return (
      <tr>
        {addCol}
        <td className="rowKey">{row.key}</td>
        <td>{row.size}</td>
        <td>{context}</td>
      </tr>
    );
  }
});
module.exports = ResultRow;
