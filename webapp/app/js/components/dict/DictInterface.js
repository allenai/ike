var React = require('react');
var bs = require('react-bootstrap');
var DictAdder = require('./DictAdder.js');
var DictList = require('./DictList.js');
var DictInterface = React.createClass({
  render: function() {
    var dicts = this.props.dicts;
    var target = this.props.target;
    var adder = <DictAdder dicts={dicts} target={target}/>;
    var list = <DictList dicts={dicts} target={target}/>;
    return (
      <div>
        {adder}
        {list}
      </div>
    );
  }
});
module.exports = DictInterface;
