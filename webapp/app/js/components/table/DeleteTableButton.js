var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var DeleteButton = require('../misc/DeleteButton.js');
var Glyphicon = bs.Glyphicon;
var DeleteTableButton = React.createClass({
  render: function() {
    var table = this.props.table;
    var deleteTable = function(e) {
      TableManager.deleteTable(table.name);
    };
    return <DeleteButton callback={deleteTable}/>;
  }
});
module.exports = DeleteTableButton;
