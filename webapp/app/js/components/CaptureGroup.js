var React = require('react');
var WordDataSeq = require('./WordDataSeq.js');
var CaptureGroup = React.createClass({
  render: function() {
    var name = this.props.name;
    var groupSeq = this.props.groupSeq;
    return (
      <td className="captureGroup">
        <div className="captureGroupName">{name}</div>
        <WordDataSeq data={groupSeq}/>
      </td>
    );
  }
});
module.exports = CaptureGroup;
