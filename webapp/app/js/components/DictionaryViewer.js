var React = require('react');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var DictionaryList = require('./DictionaryList.js');
var EntryAdder = require('./EntryAdder.js');
var DictionaryViewer = React.createClass({
  render: function() {
    var callbacks = this.props.callbacks;
    var dictionary = this.props.dictionary;
    var name = dictionary.name;
    var addPos = function(e) { callbacks.addEntry(name, "positive", e); }
    var addNeg = function(e) { callbacks.addEntry(name, "negative", e); }
    var header = <h3>{name}</h3>;
    var posTab = "Positive (" + dictionary.positive.length + ")";
    var negTab = "Negative (" + dictionary.negative.length + ")";
    return (
      <div>
        <TabbedArea eventKey={1} animation={false}>
          <TabPane eventKey={1} tab={posTab}>
            <DictionaryList
              name={name}
              entries={dictionary.positive}
              callbacks={callbacks}
              type="positive"/>
            <EntryAdder label="Positive" callback={addPos} />
          </TabPane>
          <TabPane eventKey={2} tab={negTab}>
            <DictionaryList
              name={name}
              entries={dictionary.negative}
              callbacks={callbacks}
              type="negative"/>
            <EntryAdder label="Negative" callback={addNeg} />
          </TabPane>
        </TabbedArea>
      </div>
    );
  }
});
module.exports = DictionaryViewer;
