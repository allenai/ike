var React = require('react');
var bs = require('react-bootstrap');
var ResultRow = React.createClass({
  render: function() {
    var row = this.props.row;
    var dicts = this.props.dicts;
    var target = this.props.target;
    return (
      <tr>
        <td>{row.key}</td>
        <td>{row.size}</td>
      </tr>
    );
  }
});
module.exports = ResultRow;
