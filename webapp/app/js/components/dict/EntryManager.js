var React = require('react');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var EntryList = require('./EntryList.js');
var EntryManager = React.createClass({
  entryPane: function(type) {
    var tables = this.props.tables;
    var name = this.props.name;
    var table = tables.value[name];
    var entries = table[type];
    var capType = type.charAt(0).toUpperCase() + type.slice(1);
    var tabLabel = capType + ' (' + entries.length + ')';
    var entries = <EntryList tables={tables} name={name} type={type}/>;
    return (
      <TabPane eventKey={type} tab={tabLabel}>
        {entries}
      </TabPane>
    );
  },
  render: function() {
    var posPane = this.entryPane("positive");
    var negPane = this.entryPane("negative");
    return (
      <TabbedArea animation={false}>
        {posPane}
        {negPane}
      </TabbedArea>
    );
  }
});
module.exports = EntryManager;
