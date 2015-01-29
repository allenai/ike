var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Glyphicon = bs.Glyphicon;
var DictionaryViewer = require('./DictionaryViewer.js');
var DictionaryAdder = require('./DictionaryAdder.js');
var DictionaryInterface = React.createClass({
  render: function() {
    var callbacks = this.props.callbacks;
    var dictionaries = this.props.dictionaries;
    var makeDict = function(name, i) {
      var dict = dictionaries[name];
      var deleteDict = function() {
        callbacks.deleteDictionary(name);
      };
      var header = (
        <div>
          {name}
          <Button
            onClick={deleteDict}
            bsSize="xsmall"
            className="pull-right"
            bsStyle="danger">
              <Glyphicon glyph="remove"/>
          </Button>
        </div>
      );
      return (
        <Panel header={header} eventKey={name} key={name}>
          <DictionaryViewer
            key={name}
            dictionary={dict}
            callbacks={callbacks}/>
        </Panel>
      );
    };
    var dictNames = Object.keys(dictionaries);
    var target = this.props.targetDictionary;
    return (
      <div>
        <DictionaryAdder callback={callbacks.createDictionary}/>
        <PanelGroup accordion>
          {dictNames.map(makeDict)}
        </PanelGroup>
      </div>
    );
  }
});
module.exports = DictionaryInterface;
