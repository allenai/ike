var React = require('react');
var bs = require('react-bootstrap');
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var EntryManager = require('./EntryManager.js');
var DictionaryList = React.createClass({
  deleteButton: function(name) {
    var dictLink = this.props.dictionaryLink;
    var targetLink = this.props.targetLink;
    var dicts = dictLink.value;
    var update = dictLink.requestChange;
    var deleteEntry = function() {
      if (name in dicts) {
        delete dicts[name];
        update(dicts);
        if (targetLink.value == name) {
          var dictNames = Object.keys(dicts);
          if (dictNames.length > 0) {
            targetLink.requestChange(dictNames[0]);
          } else {
            targetLink.requestChange(null);
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
  dictionaryPanel: function(name) {
    var dictLink = this.props.dictionaryLink;
    var dicts = dictLink.value;
    var dict = dicts[name];
    var button = this.deleteButton(name);
    var header = <div>{name} {button}</div>;
    var entryList = 
      <EntryManager dictionaryLink={dictLink} name={name}/>;
    return (
      <Panel header={header} key={name} eventKey={name}>
        {entryList}
      </Panel>
    );
  },
  render: function() {
    var dictLink = this.props.dictionaryLink;
    var dictionaries = dictLink.value;
    return (
      <PanelGroup accordion>
        {Object.keys(dictionaries).map(this.dictionaryPanel)}
      </PanelGroup>
    );
  }
});
module.exports = DictionaryList;
