var React = require('react');
var bs = require('react-bootstrap');
var SubTable = require('./SubTable.js');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var Table = React.createClass({
  getInitialState: function() {
    return {tableTag: this.props.table.tag};
  },

  shouldComponentUpdate: function(nextProps, nextState) {
    return nextProps.table.tag !== this.state.tableTag;
  },

  componentWillRecieveProps: function(nextProps) {
    this.setState({tableTag: nextProps.table.tag});
  },

  pane: function(rowType) {
    var table = this.props.table;
    var rows = table[rowType];
    var cap = rowType.charAt(0).toUpperCase() + rowType.slice(1);
    var title = cap + ' (' + rows.length + ')';
    return (
      <TabPane eventKey={rowType} tab={title}>
        <SubTable table={table} rowType={rowType}/>
      </TabPane>
    );
  },

  render: function() {
    var posPane = this.pane('positive');
    var negPane = this.pane('negative');
    return (
      <TabbedArea animation={false}>
        {posPane}
        {negPane}
      </TabbedArea>
    );
  }
});
module.exports = Table;
