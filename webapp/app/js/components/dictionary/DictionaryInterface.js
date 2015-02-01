var React = require('react');
var bs = require('react-bootstrap');
var DictionaryAdder = require('./DictionaryAdder.js');
var DictionaryList = require('./DictionaryList.js');
var DictionaryInterface = React.createClass({
  render: function() {
    var dictLink = this.props.dictionaryLink;
    var targetLink = this.props.targetLink;
    var adder =
      <DictionaryAdder
        dictionaryLink={dictLink}
        targetLink={targetLink}/>;
    var list =
      <DictionaryList
        dictionaryLink={dictLink}
        targetLink={targetLink}/>;
    return (
      <div>
        {adder}
        {list}
      </div>
    );
  }
});
module.exports = DictionaryInterface;
