var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var ClusterNode = React.createClass({
  render: function() {
    var node = this.props.node;
    var cluster = node.data.value;
    return (
      <div className="clusterNode">
        <Button bsSize="small">{cluster}</Button>
      </div>
    );
  }
});
module.exports = ClusterNode;
