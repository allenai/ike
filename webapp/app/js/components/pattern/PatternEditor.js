var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Button = bs.Button;
var Row = bs.Row;
var Col = bs.Col;
var SearchInterface = require("../search/SearchInterface");
var PatternStore = require("../../stores/NamedPatternsStore");

var PatternEditor = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    patternName: React.PropTypes.string.isRequired,
    config: React.PropTypes.object.isRequired,
    initialQuery: React.PropTypes.string
  },

  getInitialState: function() {
    var query = this.props.initialQuery;
    if(!query)
      query = "";
    return {
      query: query
    };
  },

  componentWillReceiveProps: function(newProps) {
    var query = newProps.initialQuery;
    if(!query)
      query = "";
    this.setState({
      query: query
    });
  },

  selectedCorpora: function() {
    return this.props.corpora.value.filter(function(corpus) {
      return corpus.selected;
    });
  },

  saveButtonClicked: function() {
    PatternStore.savePattern(this.props.patternName, this.state.query.trim());
  },

  render: function() {
    var saveAllowed =
      (!this.props.initialQuery || this.state.query.trim() !== this.props.initialQuery.trim()) &&
      (this.state.query.trim() !== "");
    var saveText = "Save as " + this.props.patternName;
    var saveButton =
      <Button disabled={!saveAllowed} onClick={this.saveButtonClicked}>{saveText}</Button>;

    return <SearchInterface
      config={this.props.config}
      target={null}
      queryLink={this.linkState('query')}
      showQueryViewer={false}
      showQuerySuggestions={false}
      buttonAfterQuery={saveButton} />;
  }
});

module.exports = PatternEditor;
