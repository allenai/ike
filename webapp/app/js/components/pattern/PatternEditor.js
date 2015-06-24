var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var CorpusSelector = require('../corpora/CorpusSelector.js');
var Input = bs.Input;
var Button = bs.Button;
var Row = bs.Row;
var Col = bs.Col;

var PatternEditor = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    corpora: React.PropTypes.object.isRequired,
    patternName: React.PropTypes.string.isRequired
  },

  getInitialState: function() {
    return {
      query: this.props.initialQuery
    };
  },

  componentWillReceiveProps: function(newProps) {
    this.setState({query: newProps.initialQuery});
  },

  selectedCorpora: function() {
    return this.props.corpora.value.filter(function(corpus) {
      return corpus.selected;
    });
  },

  render: function() {
    var toggleCorpora = function(i) {
      console.log(i);
    };

    var saveAllowed =
      (this.state.query.trim() !== this.props.initialQuery.trim()) &&
      (this.state.query.trim() !== "");
    var saveText = "Save as " + this.props.patternName;
    var saveButton = <Button disabled={!saveAllowed}>{saveText}</Button>;

    return <div>
      <CorpusSelector corpora={this.props.corpora} toggleCorpora={toggleCorpora} />
      <Input type="text"
             placeholder="Enter Query"
             label="Query"
             valueLink={this.linkState('query')}
             disabled={this.selectedCorpora().length == 0}
             buttonAfter={saveButton} />
    </div>
  }
});

module.exports = PatternEditor;
