var React = require('react');
var bs = require('react-bootstrap');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var DeleteTableButton = require('./DeleteTableButton.js');
var DownloadTableButton = require('./DownloadTableButton.js');
var TableButtonToolbar = React.createClass({
  render: function() {
    var table = this.props.table;
    var deleteButton = <DeleteTableButton table={table}/>;
    var downloadButton = <DownloadTableButton table={table}/>;
    return (
      <ButtonToolbar className="pull-right">
        <ButtonGroup>
          {downloadButton}
          {deleteButton}
        </ButtonGroup>
      </ButtonToolbar>
    );
  }
});
module.exports = TableButtonToolbar;
