var React = require('react');
var WordDataSeq = require('./WordDataSeq.js');
var Label = require('react-bootstrap/Label');
var CaptureGroup = React.createClass({
  render: function() {
    var name = this.props.name;
    var groupSeq = this.props.groupSeq;
    return (
      <td className="captureGroup">
        <WordDataSeq data={groupSeq}/>
      </td>
    );
  }
});
module.exports = CaptureGroup;
