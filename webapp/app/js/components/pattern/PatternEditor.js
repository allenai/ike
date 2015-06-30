var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Button = bs.Button;
var Row = bs.Row;
var Col = bs.Col;
var SearchInterface = require("../search/SearchInterface");

var PatternEditor = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    patternName: React.PropTypes.string.isRequired,
    config: React.PropTypes.object.isRequired
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

    var saveAllowed =
      (this.state.query.trim() !== this.props.initialQuery.trim()) &&
      (this.state.query.trim() !== "");
    var saveText = "Save as " + this.props.patternName;
    var saveButton = <Button disabled={!saveAllowed}>{saveText}</Button>;

    return <SearchInterface
      config={this.props.config}
      target={null}
      queryLink={this.linkState('query')}
      showQueryViewer={false}
      buttonAfterQuery={saveButton} />;
  }
});

module.exports = PatternEditor;
