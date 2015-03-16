var React = require('react/addons');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var DeleteButton = React.createClass({
  render: function() {
    var callback = this.props.callback;
    return (
      <Button
        onClick={callback}
        bsSize="xsmall"
        className="pull-right"
        bsStyle="danger">
        <Glyphicon glyph="remove"/>
      </Button>
    );
  }
});
module.exports = DeleteButton;
