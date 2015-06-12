var React = require('react/addons');

var PatternsInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  getInitialState: function() {
    return {
      patternName: null
    };
  },
  render: function() {
    return <div>I am a patterns interface!</div>
  }
});

module.exports = PatternsInterface;
