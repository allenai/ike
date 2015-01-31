var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var POSNode = React.createClass({
  render: function() {
    var node = this.props.node;
    var pos = node.data.value;
    return (
      <div className="posNode">
        <Button bsSize="small">{pos}</Button>
      </div>
    );
  }
});
module.exports = POSNode;
