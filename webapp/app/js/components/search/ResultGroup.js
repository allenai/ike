var React = require('react');
var bs = require('react-bootstrap');
var AddResultButton = require('./AddResultButton.js');
var ResultContextSet = require('./ResultContextSet.js');
var ResultGroup = React.createClass({
  addCol: function() {
    var target = this.props.target;
    var group = this.props.group;
    if (target.value == null) { 
      return null
    } else {
      return <td><AddResultButton group={group} target={target}/></td>;
    }
  },
  keyCols: function() {
    var cols = this.props.cols;
    if (cols == null || cols.length == 0) {
      return null;
    } else {
      var values = this.props.group.keys;
      var makeCol = function(value, i) {
        return <td key={i}>{value}</td>;
      };
      return values.map(makeCol);
    }
  },
  render: function() {
    var group = this.props.group;
    var context = <ResultContextSet group={group}/>;
    return (
      <tr>
        {this.addCol()}
        {this.keyCols()}
        <td>{group.size}</td>
        <td>{context}</td>
      </tr>
    );
  }
});
module.exports = ResultGroup;
