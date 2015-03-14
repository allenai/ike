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
      var csv = TableManager.table2csv(table);
      var encodedCsv = encodeURIComponent(csv);
      var pom = document.createElement('a');
      pom.setAttribute('href', 'data:text/csv;charset=utf-8,' + encodedCsv);
      pom.setAttribute('target', '_blank');
      pom.setAttribute('download', table.name + ".dict.csv");
      document.body.appendChild(pom);
      setTimeout(function() {
        pom.click();
        document.body.removeChild(pom);
      }, 50);
    };
    var icon = <Glyphicon glyph="download"/>;
    return <Button onClick={downloadDict} bsSize="xsmall">{icon}</Button>;
  }
});
module.exports = DownloadTableButton;
