var React = require('react/addons');
var bs = require('react-bootstrap');
var OverlayTrigger = bs.OverlayTrigger;
var Popover = bs.Popover;

var WordData = React.createClass({
  render: function() {
    var attrs = this.props.attributes;
    var word = this.props.word;
    var highlighted = this.props.highlighted;
    var createAttr = function(name, index) {
      return (<li key={name}>{name} = {attrs[name]}</li>);
    };
    var cx = React.addons.classSet;
    var classes = cx({
      'highlighted': highlighted,
      'wordData': true
    });
    return (
      <span className={classes}>
        <span className="word">{word}</span>
      </span>
    );
  }
});

module.exports = WordData;
