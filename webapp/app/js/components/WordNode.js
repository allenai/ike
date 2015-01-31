var React = require('react');
var bs = require('react-bootstrap');
var Button = bs.Button;
var WordNode = React.createClass({
  render: function() {
    var node = this.props.node;
    var word = node.data.value;
    console.log(word);
    return (
      <div className="wordNode">
        <Button bsSize="small">{word}</Button>
      </div>
    );
  }
});
module.exports = WordNode;
