var React = require('react');
var bs = require('react-bootstrap');
var ListGroup = bs.ListGroup;
var ListGroupItem = bs.ListGroupItem;
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var DictionaryList = React.createClass({
  componentWillUpdate: function() {
    var node = this.getDOMNode();
    var pos = node.scrollTop + node.offsetHeight;
    this.shouldScrollBottom = pos == node.scrollHeight;
  },
  componentDidUpdate: function() {
    if (this.shouldScrollBottom) {
      var node = this.getDOMNode();
      node.scrollTop = node.scrollHeight
    }
  },
  render: function() {
    var name = this.props.name;
    var entries = this.props.entries;
    var callbacks = this.props.callbacks;
    var type = this.props.type;
    var makeEntry = function(entry, i) {
      var key = name + '.' + entry;
      var deleteEntry = function() {
        callbacks.deleteEntry(name, type, entry);
      };
      return (
        <ListGroupItem key={key}>
          {entry}
          <Button
            onClick={deleteEntry}
            bsSize="xsmall"
            className="pull-right"
            bsStyle="danger">
            <Glyphicon glyph="remove"/>
          </Button>
        </ListGroupItem>
      );
    };
    return (
      <div className="dictList">
        <ListGroup>
          {entries.map(makeEntry)}
        </ListGroup>
      </div>
    );
  }
});
module.exports = DictionaryList;
