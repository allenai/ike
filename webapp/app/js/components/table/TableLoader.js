var React = require('react/addons');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Label = bs.Label;
var Alert = bs.Alert;
var TableManager = require('../../managers/TableManager.js');

var TableLoader = React.createClass({
  getInitialState: function() {
    return {
      name: '',
      cols: [],
      positive: [],
      negative: [],
      error: ''};
  },

  handleSubmit: function(e) {
    e.preventDefault();
    var table = {
      name: this.state.name,
      cols: this.state.cols,
      positive: this.state.positive,
      negative: this.state.negative
    };
    this.props.onSubmit(table);
    this.setState(this.getInitialState());
  },

  submitDisabled: function() {
    var name = this.state.name;
    var error = this.state.error;
    var cols = this.state.cols;
    return name.trim() == '' || error.trim() != '' || cols.length == 0;
  },

  handleFile: function(e) {
    var self = this;
    var reader = new FileReader();
    var file = e.target.files[0];

    reader.onload = function(upload) {
      var dataUrl = upload.target.result;
      var data = dataUrl.substr(dataUrl.indexOf(',') + 1);
      var buffer = new Buffer(data, 'base64');
      var text = buffer.toString();
      var trimAll = function(strings) { return strings.map(function(s) { return s.trim(); }) };
      var nonemptyLine = function(line) { return line.trim().length != 0 };
      var lines = text.split('\n').filter(nonemptyLine).map(function(line) {
        return trimAll(line.split(','));
      });

      // read the headers
      if(lines.length == 0) {
        self.setState({error: "Input file is empty."});
        return;
      };
      var headers = lines[0]
      if(headers[headers.length - 1] != "label") {
        self.setState({error: "Input file has no label column."});
        return;
      };
      var newCols = headers.slice(0, headers.length - 1);

      // read the body
      body = lines.slice(1);
      if(!body.every(function(line) { return line.length == headers.length })) {
        self.setState({error: "Input file has lines with an invalid number of columns."});
        return;
      };

      var newPositive = body.filter(function(line) {
        return line[line.length - 1] == "positive";
      }).map(function(line) {
        return line.slice(0, line.length - 1);
      });

      var newNegative = body.filter(function(line) {
        return line[line.length - 1] == "negative";
      }).map(function(line) {
        return line.slice(0, line.length - 1);
      });

      var makeQWords = function(ws) { return TableManager.stringsRow(ws); }

      self.setState({
        cols: newCols,
        positive: newPositive.map(makeQWords),
        negative: newNegative.map(makeQWords),
        error: ''
      });
    }

    reader.readAsDataURL(file);
  },

  handleNameChange: function(e) {
    this.setState({name: e.target.value});
  },

  nameInput: function() {
    var label = "Table Name";
    var placeholder = "Enter Table Name";
    var onChange = this.handleNameChange;
    var value = this.state.name;
    return <Input
      type="text"
      label={label}
      value={this.state.name}
      placeholder={placeholder}
      onChange={onChange}/>;
  },

  submitButton: function() {
    return <Input
      type="submit"
      value="Create Table"
      disabled={this.submitDisabled()}
      onClick={this.handleSubmit}/>;
  },

  render: function() {
    var nameInput = this.nameInput();
    var fileUpload = <Input type="file" label="File" onChange={this.handleFile} />;
    var alert = <Alert bsStyle="danger">{this.state.error}</Alert>
    var submitButton = this.submitButton();
    if(this.state.error.length == 0)
      return <div>{nameInput}{fileUpload}{submitButton}</div>
    else
      return <div>{nameInput}{fileUpload}{alert}{submitButton}</div>
  }
});
module.exports = TableLoader;
