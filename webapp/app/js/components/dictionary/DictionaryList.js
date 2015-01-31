var React = require('react');
var bs = require('react-bootstrap');
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var DictionaryList = React.createClass({
  deleteButton: function(name) {
    var dicts = this.props.dictionaries;
    var update = this.props.updateDictionaries;
    var deleteEntry = function() {
      if (name in dicts) {
        delete dicts[name];
        update(dicts);
      }
    };
    return <Button 
             onClick={deleteEntry}
             bsSize="xsmall"
             className="pull-right"
             bsStyle="danger"><Glyphicon glyph="remove"/></Button>
  },
  dictionaryPanel: function(name) {
    var dict = this.props.dictionaries[name];
    var button = this.deleteButton(name);
    var header = <div>{name} {button}</div>;
    return (
      <Panel header={header} key={name} eventKey={name}>
        Contents.
      </Panel>
    );
  },
  render: function() {
    var dictionaries = this.props.dictionaries;
    var updateDictionaries = this.props.updateDictionaries;
    return (
      <PanelGroup accordion>
        {Object.keys(dictionaries).map(this.dictionaryPanel)}
      </PanelGroup>
    );
  }
});
module.exports = DictionaryList;
