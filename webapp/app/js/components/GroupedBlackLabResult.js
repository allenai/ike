var React = require('react');
var bs = require('react-bootstrap');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var KeyedBlackLabResults = require('./KeyedBlackLabResults.js');
var GroupedBlackLabResult = React.createClass({
  makeButton: function() {
    var addEntry = this.props.callbacks.addEntry;
    var deleteEntry = this.props.callbacks.deleteEntry;
    var isPositive = this.props.callbacks.isPositive;
    var isNegative = this.props.callbacks.isNegative;
    var result = this.props.result;
    var entry = result.key;
    var target = this.props.targetDictionary;
    var toggleFn = this.props.callbacks.toggle;
    var toggle = function(type) {
      return function() { toggleFn(target, type, entry); };
    };
    var toggleNeg = toggle('negative');
    var togglePos = toggle('positive');
    if (target != null) {
      var posStyle = isPositive(target, entry) ? 'primary' : 'default';
      var negStyle = isNegative(target, entry) ? 'warning' : 'default';
      var style = {'float': 'none', 'display': 'inline-block'}; 
      return (
        <ButtonToolbar>
          <ButtonGroup bsSize="small" style={{"white-space": "nowrap"}}>
            <Button style={style} onClick={togglePos} bsStyle={posStyle}>{target}</Button>
            <Button style={style} onClick={toggleNeg} bsStyle={negStyle}>not {target}</Button>
          </ButtonGroup>
        </ButtonToolbar>
      );
    }
  },
  render: function() {
    var result = this.props.result;
    var button = this.makeButton();
    return (
      <tr>
        <td style={{width: "1%", "white-space":"nowrap"}}>{button}</td>
        <td>{result.key}</td>
        <td>{result.size}</td>
        <td>
          <KeyedBlackLabResults keyedResults={result.group}/>
        </td>
      </tr>
    );
  }
});
module.exports = GroupedBlackLabResult;
