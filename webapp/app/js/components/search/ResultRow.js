var React = require('react');
var bs = require('react-bootstrap');
var AddResultButton = require('./AddResultButton.js');
var ResultRow = React.createClass({
  render: function() {
    var row = this.props.row;
    var dicts = this.props.dicts;
    var target = this.props.target;
    var addCol;
    if (target.value == null) { 
      addCol = null
    } else {
      addCol =
        <td><AddResultButton row={row} target={target} dicts={dicts}/></td>;
    }
    return (
      <tr>
        {addCol}
        <td>{row.key}</td>
        <td>{row.size}</td>
      </tr>
    );
  }
});
module.exports = ResultRow;
