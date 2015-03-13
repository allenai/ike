var React = require('react');
var bs = require('react-bootstrap');
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Button = bs.Button;
var ButtonToolbar = bs.ButtonToolbar
var ButtonGroup = bs.ButtonGroup
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
             bsStyle="danger"><Glyphicon glyph="remove"/></Button>
  },
  downloadButton: function(name) {
    var dict = this.props.dicts.value[name];
    var downloadDict = function(e) {
      e.stopPropagation();

      var csv =
        dict.positive.map(function(x) { return x + ",positive\n" }).join("") +
        dict.negative.map(function(x) { return x + ",negative\n" }).join("");

      var pom = document.createElement('a');
      pom.setAttribute('href', 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv));
      pom.setAttribute('target', '_blank');
      pom.setAttribute('download', name + ".dict.csv");
      document.body.appendChild(pom);
      setTimeout(function() {
        pom.click();
        document.body.removeChild(pom);
      }, 50);
    }
    return <Button
      onClick={downloadDict}
      bsSize="xsmall"><Glyphicon glyph="download"/></Button>
  },
  dictPanel: function(name) {
    var dicts = this.props.dicts;
    var dict = dicts.value[name];
    var button = this.deleteButton(name);
    var download = this.downloadButton(name);
    var header = <div>{name} <ButtonToolbar className="pull-right">
      <ButtonGroup>{download}</ButtonGroup>
      <ButtonGroup>{button}</ButtonGroup>
      </ButtonToolbar></div>;
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
