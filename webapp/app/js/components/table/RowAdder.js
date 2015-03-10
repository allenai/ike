var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var RowAdder = React.createClass({
  emptyValues: function() {
    return this.props.cols.map(function(col, i) { return ''; });
  },
  getInitialState: function() {
    return {values: this.emptyValues()};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.onSubmit(this.state.values);
    var firstField = this.refs.col0.getDOMNode();
    this.setState({values: this.emptyValues()}, function() {
      firstField.focus();
    });
  },
  handleChange: function(i) {
    return function(e) {
      if (e.which == 13) {
        this.handleSubmit(e);
      } else {
        var value = e.target.value;
        this.state.values[i] = value;
        this.setState({values: this.state.values});
      }
    }.bind(this);
  },
  columnInput: function(col, i) {
    var key = col + '.' + i;
    var value = this.state.values[i];
    var onChange = this.handleChange(i);
    var ph = "Add " + col;
    var ref = "col" + i;
    var input = <input
      ref={ref}
      className="form-control"
      onKeyPress={onChange}
      type="text" placeholder={ph} value={value} onChange={onChange}/>;
    return <td key={key}>{input}</td>;
  },
  render: function() {
    var cols = this.props.cols;
    var inputs = cols.map(this.columnInput);
    var row = <tr><td>New Row:</td>{inputs}</tr>;
    return row;
  }
});
module.exports = RowAdder;
