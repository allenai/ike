var React = require('react');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var EntryList = require('./EntryList.js');
var EntryManager = React.createClass({
  entryPane: function(type) {
    var dictLink = this.props.dictionaryLink;
    var name = this.props.name;
    var dicts = dictLink.value;
    var dict = dicts[name];
    var entries = dict[type];
    var capType = type.charAt(0).toUpperCase() + type.slice(1);
    var tabLabel = capType + ' (' + entries.length + ')';
    var entries = <EntryList dictionaryLink={dictLink} name={name} type={type}/>;
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
