var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var ListGroup = bs.ListGroup;
var ListGroupItem = bs.ListGroupItem;
var Glyphicon = bs.Glyphicon;
var Button = bs.Button;
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var Button = bs.Button;

var DictionaryAdder = React.createClass({
  getInitialState: function() {
    return {value: ''};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callback(this.state.value);
    this.setState({value: ''});
  },
  onChange: function(e) {
    this.setState({value: e.target.value});
  },
  render: function() {
    return (
      <form onSubmit={this.handleSubmit}>
        <Input type="text" onChange={this.onChange} value={this.state.value} placeholder="Create New Dictionary"/>
      </form>
    );
  }
});

var EntryAdder = React.createClass({
  getInitialState: function() {
    return {value: ''};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callback(this.state.value);
    this.setState({value: ''});
  },
  onChange: function(e) {
    this.setState({value: e.target.value});
  },
  render: function() {
    var text = "Add " + this.props.label + " Entry";
    return (
      <form onSubmit={this.handleSubmit}>
        <Input type="text" onChange={this.onChange} value={this.state.value} placeholder={text}/>
      </form>
    );
  },
});

var DictionaryList = React.createClass({
  componentWillUpdate: function() {
    var node = this.getDOMNode();
    this.shouldScrollBottom = node.scrollTop + node.offsetHeight === node.scrollHeight;
  },
  componentDidUpdate: function() {
    if (this.shouldScrollBottom) {
      var node = this.getDOMNode();
      node.scrollTop = node.scrollHeight
    }
  },
  render: function() {
    var name = this.props.name;
    var entries = this.props.entries;
    var callbacks = this.props.callbacks;
    var type = this.props.type;
    var makeEntry = function(entry, i) {
      var key = name + '.' + entry;
      var deleteEntry = function() {
        callbacks.deleteEntry(name, type, entry);
      };
      return (
        <ListGroupItem key={key}>
          {entry}
          <Button onClick={deleteEntry} bsSize="xsmall" className="pull-right" bsStyle="danger"><Glyphicon glyph="remove"/></Button>
        </ListGroupItem>
      );
    };
    return (
      <div className="dictList">
        <ListGroup> 
          {entries.map(makeEntry)}
        </ListGroup>
      </div>
    );
  }
});

var DictionaryViewer = React.createClass({
  render: function() {
    var callbacks = this.props.callbacks;
    var dictionary = this.props.dictionary;
    var name = dictionary.name;
    var addPos = function(entry) { callbacks.addEntry(name, "positive", entry); }
    var addNeg = function(entry) { callbacks.addEntry(name, "negative", entry); }
    var header = <h3>{name}</h3>;
    var posTab = "Positive Instances (" + dictionary.positive.length + ")";
    var negTab = "Positive Instances (" + dictionary.negative.length + ")";
    return (
      <div>

        <TabbedArea eventKey={1} animation={false}>
          <TabPane eventKey={1} tab={posTab}>
            <DictionaryList name={name} entries={dictionary.positive} callbacks={callbacks} type="positive"/>
            <EntryAdder label="Positive" callback={addPos} />
          </TabPane>
          <TabPane eventKey={2} tab={negTab}>
            <DictionaryList name={name} entries={dictionary.negative} callbacks={callbacks} type="negative"/>
            <EntryAdder label="Negative" callback={addNeg} />
          </TabPane>
        </TabbedArea>

      </div>
    );
  }
});

var DictionaryInterface = React.createClass({
  getInitialState: function() {
    return {selected: Object.keys(this.props.dictionaries)[0]};
  },
  render: function() {
    var callbacks = this.props.callbacks;
    var dictionaries = this.props.dictionaries;
    var makeDict = function(name, i) {
      var dict = dictionaries[name];
      var deleteDict = function() {
        callbacks.deleteDictionary(name);
      };
      var header = <div>{name}<Button onClick={deleteDict} bsSize="xsmall" className="pull-right" bsStyle="danger"><Glyphicon glyph="remove"/></Button></div>;
      return (
        <Panel header={header} eventKey={i}>
          <DictionaryViewer key={name} dictionary={dict} callbacks={callbacks}/>
        </Panel>
      );
    };
    var dictNames = Object.keys(dictionaries);
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
