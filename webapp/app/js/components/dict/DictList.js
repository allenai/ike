var React = require('react');
var bs = require('react-bootstrap');
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var EntryManager = require('./EntryManager.js');
var DictList = React.createClass({
  deleteButton: function(name) {
    var dicts = this.props.dicts;
    var target = this.props.target;
    var deleteEntry = function() {
      if (name in dicts.value) {
        delete dicts.value[name];
        dicts.requestChange(dicts.value);
        var dictNames = Object.keys(dicts.value);
        if (target.value == name) {
          if (dictNames.length > 0) {
            target.requestChange(dictNames[0]);
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
    var dicts = this.props.dicts;
    var dict = dicts.value[name];
    var button = this.deleteButton(name);
    var header = <div>{name} {button}</div>;
    var entryList = <EntryManager dicts={dicts} name={name}/>;
    return (
      <Panel header={header} key={name} eventKey={name}>
        {entryList}
      </Panel>
    );
  },
  render: function() {
    var dicts = this.props.dicts;
    return (
      <PanelGroup accordion>
        {Object.keys(dicts.value).map(this.dictPanel)}
      </PanelGroup>
    );
  }
});
module.exports = DictList;
