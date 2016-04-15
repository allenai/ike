var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var DownloadTableButton = React.createClass({
  render: function() {
    var table = this.props.table;
    var downloadDict = function(e) {
      e.stopPropagation();
      var tsv = TableManager.table2csv(table);
      var blob = new Blob([tsv], {type: 'text/tsv'});
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = table.name + ".dict.tsv";
      document.body.appendChild(a);
      setTimeout(function() {
        a.click();
        document.body.removeChild(a);
      }, 50);
    var icon = <Glyphicon glyph="download"/>;
    return <Button onClick={downloadDict} bsSize="xsmall">{icon}</Button>;
  }
});
module.exports = DownloadTableButton;
