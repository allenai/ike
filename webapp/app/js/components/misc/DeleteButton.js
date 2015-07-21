var React = require('react/addons');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;

var DeleteButton = React.createClass({
  propTypes: {
    callback: React.PropTypes.func.isRequired,
    bsStyle: React.PropTypes.string
  },

  render: function() {
    var callback = this.props.callback;

    var bsStyle = this.props.bsStyle;
    if(!bsStyle)
      bsStyle = "danger";

    return (
      <Button
        onClick={callback}
        bsSize="xsmall"
        className="pull-right"
        bsStyle={bsStyle}>
        <Glyphicon glyph="remove"/>
      </Button>
    );
  }
});

module.exports = DeleteButton;
