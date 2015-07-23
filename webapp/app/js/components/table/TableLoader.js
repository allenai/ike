var React = require('react/addons');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Label = bs.Label;
var Alert = bs.Alert;
var TableManager = require('../../managers/TableManager.js');

var TableLoader = React.createClass({
  componentDidMount: function() {
    var callback = function() {
      // Since this is a callback, the component could have been unmounted in the meantime.
      if(this.isMounted()) {
        if(TableManager.userEmail()) {
          this.setState({error: ''});
        } else {
          this.setState({error: "You must be logged in to create tables."});
        }
      }
    }.bind(this);

    TableManager.addChangeListener(callback);
    callback();
  },

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
        return trimAll(line.split('\t'));
      });

      // read the headers
      if(lines.length == 0) {
        self.setState({error: "Input file is empty."});
        return;
      }
      var headers = lines[0];
      if(headers[headers.length - 2] != "label") {
        self.setState({error: "Input file has no label column."});
        return;
      }
      if(headers[headers.length - 1] != "provenance") {
        self.setState({error: "Input file has no provenance column."});
        return;
      }
      var newCols = headers.slice(0, headers.length - 2);

      // read the body
      var body = lines.slice(1);
      var errorLines = body.filter(function(line) { return line.length != headers.length; });
      if(errorLines.length != 0) {
        self.setState({error: "Input file has a line with an invalid number of columns: " + errorLines[0]});
        return;
      }

      // make strings into rows
      var makeRow = function(line) {
        var result = TableManager.stringsRow(line.slice(0, line.length - 2));

        var provenanceJson = line[line.length - 1];
        result.provenance = {};
        if(provenanceJson) {
          try {
            result.provenance = JSON.parse(provenanceJson);
          } catch(e) {
            throw {
              "message": "JSON error",
              "json": provenanceJson,
              "inner": e
            };
          }
        }
        return result;
      };

      var filterLabel = function(label) {
        return function(line) {
          return line[line.length - 2] === label;
        };
      };
      try {
        var newPositive = body.filter(filterLabel("positive")).map(makeRow);
        var newNegative = body.filter(filterLabel("negative")).map(makeRow);
      } catch(e) {
        if(e.message === "JSON error") {
          self.setState({error: "JSON error on line '" + e.json + "'"});
          return;
        } else {
          throw e;
        }
      }

      self.setState({
        cols: newCols,
        positive: newPositive,
        negative: newNegative,
        error: ''
      });
    };

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
      onChange={onChange}
      disabled={!TableManager.userEmail()} />;
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
    var fileUpload = <Input
      type="file"
      label="File"
      onChange={this.handleFile}
      disabled={!TableManager.userEmail()} />;
    var alert = <Alert bsStyle="danger">{this.state.error}</Alert>;
    var submitButton = this.submitButton();
    if(this.state.error.length == 0)
      return <div>{nameInput} {fileUpload} {submitButton}</div>;
    else
      return <div>{nameInput} {fileUpload} {alert} {submitButton}</div>;
  }
});
module.exports = TableLoader;
