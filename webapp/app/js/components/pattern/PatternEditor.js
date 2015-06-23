var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var SearchForm = require('../search/SearchForm.js');

var PatternEditor = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  getInitialState: function() {
    return {
      query: this.props.initialQuery
    };
  },

  render: function() {
    return <SearchForm showTargetSelector={false} showQuerySuggestions={false} />
  }
});

module.exports = PatternEditor;
