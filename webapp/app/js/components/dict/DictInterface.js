var React = require('react');
var bs = require('react-bootstrap');
var DictAdder = require('./DictAdder.js');
var DictList = require('./DictList.js');
var DictInterface = React.createClass({
  render: function() {
    var tables = this.props.tables;
    var target = this.props.target;
    var adder = <DictAdder tables={tables} target={target}/>;
    var list = <DictList tables={tables} target={target}/>;
    return (
      <div>
        {adder}
        {list}
      </div>
    );
  }
});
module.exports = DictInterface;
