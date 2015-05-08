var React = require('react');
var bs = require('react-bootstrap');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var TableManager = require('../../managers/TableManager.js');
var AddResultButton = React.createClass({
  row: function() {
    var group = this.props.group;
    var values = group.keys;

    var provenance = {
      "query": this.props.query.value,
      "context": group.results.map(function(resultObject) {
        var words = resultObject.result.wordData;
        var fragment = words.map(function(word) { return word.word; }).join(" ");
        var matchOffset = resultObject.result.matchOffset;
        var corpus = resultObject.result.corpusName;
        return {
          "fragment": fragment,
          "words": words,
          "matchOffset": matchOffset,
          "corpus": corpus
        };
      }),
    };

    var row = TableManager.stringsRow(values);
    row.provenance = provenance
    return row
  },
  isType: function(type) {
    var target = this.props.target.value;
    return TableManager.hasRow(target, type, this.row());
  },
  isPos: function() {
    return this.isType('positive');
  },
  isNeg: function() {
    return this.isType('negative');
  },
  toggleType: function(type) {
    var target = this.props.target.value;
    TableManager.toggleRow(target, type, this.row());
  },
  togglePos: function() {
    this.toggleType('positive');
  },
  toggleNeg: function() {
    this.toggleType('negative');
  },
  posStyle: function() {
    return this.isPos() ? 'primary' : 'default';
  },
  negStyle: function() {
    return this.isNeg() ? 'warning' : 'default';
  },
  render: function() {
    var target = this.props.target.value;
    return (
      <ButtonToolbar>
        <ButtonGroup bsSize="small" style={{display: 'flex'}}>
          <Button onClick={this.togglePos} bsStyle={this.posStyle()}>
            +
          </Button>
          <Button onClick={this.toggleNeg} bsStyle={this.negStyle()}>
            -
          </Button>
        </ButtonGroup>
      </ButtonToolbar>
    );
  }
});
module.exports = AddResultButton;
