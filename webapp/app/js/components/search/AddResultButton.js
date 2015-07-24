var React = require('react');
var bs = require('react-bootstrap');
var ButtonToolbar = bs.ButtonToolbar;
var ButtonGroup = bs.ButtonGroup;
var Button = bs.Button;
var TableManager = require('../../managers/TableManager.js');
var Glyphicon = bs.Glyphicon;

var AddResultButton = React.createClass({
  getInitialState: function() {
    return {
      isPos: this.isPos(),
      isNeg: this.isNeg()
    };
  },

  tableDidUpdate: function() {
    this.setState(this.getInitialState());
  },

  componentWillMount: function() {
    TableManager.addChangeListener(this.tableDidUpdate);
  },

  componentWillUnmount: function() {
    TableManager.removeChangeListener(this.tableDidUpdate);
  },

  componentDidUpdate: function(prevProps, prevState) {
    if(prevProps.target.value !== this.props.target.value)
      this.setState(this.getInitialState());
  },

  row: function() {
    var group = this.props.group;
    var values = group.keys;

    var provenance = {
      "query": this.props.query,
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
      })
    };

    var row = TableManager.stringsRow(values);
    row.provenance = provenance;
    return row;
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
    return this.state.isPos ? 'primary' : 'default';
  },

  negStyle: function() {
    return this.state.isNeg ? 'warning' : 'default';
  },

  render: function() {
    var target = this.props.target.value;
    return (
      <ButtonToolbar>
        <ButtonGroup bsSize="small" style={{display: 'flex'}}>
          <Button onClick={this.togglePos} bsStyle={this.posStyle()}>
            <Glyphicon glyph="plus" />
          </Button>
          <Button onClick={this.toggleNeg} bsStyle={this.negStyle()}>
            <Glyphicon glyph="minus" />
          </Button>
        </ButtonGroup>
      </ButtonToolbar>
    );
  }
});

module.exports = AddResultButton;
