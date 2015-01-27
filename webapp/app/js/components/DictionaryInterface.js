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
    var makeEntry = function(entry) {
      var key = name + '.' + entry;
      return <ListGroupItem key={key}>{entry}</ListGroupItem>;
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
    return (
      <Panel header={header}> 

        <TabbedArea defaultActiveKey={1} animation={false}>
          <TabPane eventKey={1} tab="Positive Instances">
            <DictionaryList name={name} entries={dictionary.positive}/>
            <DictionaryAdder label="Positive" callback={addPos} />
          </TabPane>
          <TabPane eventKey={2} tab="Negative Instances">
            <DictionaryList name={name} entries={dictionary.negative}/>
            <DictionaryAdder label="Negative" callback={addNeg} />
          </TabPane>
        </TabbedArea>

      </Panel>
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
    var makeDict = function(name) {
      var dict = dictionaries[name];
      return <DictionaryViewer key={name} dictionary={dict} callbacks={callbacks} />;
    };
    var dictNames = Object.keys(dictionaries);
    return (
      <PanelGroup accordion>
        {dictNames.map(makeDict)}
      </PanelGroup>
    );
  }
});
module.exports = DictionaryInterface;
