var React = require('react');
var bs = require('react-bootstrap');
var DictionaryAdder = require('./DictionaryAdder.js');
var DictionaryList = require('./DictionaryList.js');
var DictionaryInterface = React.createClass({
  render: function() {
    var dictionaries = this.props.dictionaries;
    var updateDictionaries = this.props.updateDictionaries;
    var adder = 
      <DictionaryAdder
        dictionaries={dictionaries}
        updateDictionaries={updateDictionaries}/>;
    var list =
      <DictionaryList
        dictionaries={dictionaries}
        updateDictionaries={updateDictionaries}/>;
    return (
      <div>
        {adder}
        {list}
      </div>
    );
  }
});
module.exports = DictionaryInterface;
