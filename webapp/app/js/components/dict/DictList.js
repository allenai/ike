var React = require('react');
var bs = require('react-bootstrap');
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var EntryManager = require('./EntryManager.js');
var DictList = React.createClass({
  deleteButton: function(name) {
    var tables = this.props.tables;
    var target = this.props.target;
    var deleteEntry = function() {
      if (name in tables.value) {
        delete tables.value[name];
        tables.requestChange(tables.value);
        var tableNames = Object.keys(tables.value);
        if (target.value == name) {
          if (tableNames.length > 0) {
            target.requestChange(tableNames[0]);
          } else {
            target.requestChange(null);
          }
        }
      }
    };
    return <Button 
             onClick={deleteEntry}
             bsSize="xsmall"
             className="pull-right"
             bsStyle="danger"><Glyphicon glyph="remove"/></Button>
  },
  dictPanel: function(name) {
    var tables = this.props.tables;
    var table = tables.value[name];
    var button = this.deleteButton(name);
    var header = <div>{name} {button}</div>;
    var entryList = <EntryManager tables={tables} name={name}/>;
    return (
      <Panel header={header} key={name} eventKey={name}>
        {entryList}
      </Panel>
    );
  },
  render: function() {
    var tables = this.props.tables;
    return (
      <PanelGroup accordion>
        {Object.keys(tables.value).map(this.dictPanel)}
      </PanelGroup>
    );
  }
});
module.exports = DictList;
