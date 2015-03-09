var React = require('react');
var bs = require('react-bootstrap');
var tableUtils = require('../../tableUtils.js');
var Input = bs.Input;
var EntryAdder = React.createClass({
  getInitialState: function() {
    return {value: ""};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    var entryString = this.state.value.trim();
    if (entryString == '') {
      return;
    }
    var tables = this.props.tables;
    var name = this.props.name;
    var type = this.props.type;
    var entries = tables.value[name][type];
    var entryStrings = entries.map(tableUtils.rowString);
    var index = entryStrings.indexOf(entryString);
    if (index < 0) {
      var entry = tableUtils.stringToRow(entryString);
      entries.unshift(entry);
      tables.requestChange(tables.value);
    }
    this.setState({value: ''});
  },
  onChange: function(e) {
    this.setState({value: e.target.value});
  },
  render: function() {
    var name = this.props.name;
    var type = this.props.type;
    var placeholder = "Add " + type + " entry to " + name;
    return (
      <form onSubmit={this.handleSubmit}>
        <Input
          type="text"
          value={this.state.value}
          onChange={this.onChange}
          placeholder={placeholder}/>
      </form>
    );
  }
});
module.exports = EntryAdder;
