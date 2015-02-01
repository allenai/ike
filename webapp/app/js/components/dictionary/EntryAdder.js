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
    var dictLink = this.props.dictionaryLink;
    var dicts = dictLink.value;
    var update = dictLink.requestChange;
    var name = this.props.name;
    var type = this.props.type;
    var entries = dicts[name][type];
    if (entries.indexOf(entry) < 0) {
      entries.unshift(entry);
      update(dicts);
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
