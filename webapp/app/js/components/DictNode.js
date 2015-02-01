var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var DictNode = React.createClass({
  render: function() {
    var node = this.props.node;
    var name = node.data.value;
    return (
      <div className="dictNode">
        <Button bsSize="small">{name}</Button>
      </div>
    );
  }
});
module.exports = DictNode;
