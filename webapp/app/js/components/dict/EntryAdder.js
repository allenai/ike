var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var EntryAdder = React.createClass({
  getInitialState: function() {
    return {value: ""};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    var entry = this.state.value.trim();
    if (entry == '') {
      return;
    }
    var dicts = this.props.dicts;
    var name = this.props.name;
    var type = this.props.type;
    var entries = dicts.value[name][type];
    if (entries.indexOf(entry) < 0) {
      entries.unshift(entry);
      dicts.requestChange(dicts.value);
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
