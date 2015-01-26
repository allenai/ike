var React = require('react');
var bs = require('react-bootstrap');
var OverlayTrigger = bs.OverlayTrigger;
var Popover = bs.Popover;

var WordData = React.createClass({
  render: function() {
    var attrs = this.props.attributes;
    var word = this.props.word;
    var createAttr = function(name, index) {
      return (<li key={name}>{name} = {attrs[name]}</li>);
    };
    return (
      <span className="wordData">
        <span className="word">{word}</span>
      </span>
    );
  }
});

module.exports = WordData;
