var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Accordion = bs.Accordion;
var Panel = bs.Panel;
var TableAdder = require('./TableAdder.js');
var TableLoader = require('./TableLoader.js');
var Table = require('./Table.js');
var TableManager = require('../../managers/TableManager.js');
var TableButtonToolbar = require('./TableButtonToolbar.js');
const AuthStore = require('../../stores/AuthStore.js');

var TablesInterface = React.createClass({

  // This mixin makes this interface require authentication
  mixins: [ AuthStore.Mixin ],

  tables: function() {
    var tables = TableManager.getTables();
    var components = Object.keys(tables).map(function(name, i) {
      var table = tables[name];
      var buttons = <TableButtonToolbar table={table}/>;
      var header = <span>{name} {buttons}</span>;
      return (
        <Panel header={header} key={name} eventKey={i}>
          <Table key={name} table={table}/>
        </Panel>
      );
    }.bind(this));
    return <Accordion>{components}</Accordion>;
  },

  addTable: function(table) {
    TableManager.createTable(table);
    this.props.target.requestChange(table.name);
  },

  adder: function() {
    return (
      <div>
        <Panel header="Create New Table">
          <TableAdder onSubmit={this.addTable}/>
        </Panel>
        <Panel header="Upload Table">
          <TableLoader onSubmit={this.addTable}/>
        </Panel>
      </div>
    );
  },

  render: function() {
    return (
       <Row>
          <Col xs={3}>{this.adder()}</Col>
          <Col xs={7}>{this.tables()}</Col>
       </Row>
    );
  }
});
module.exports = TablesInterface;
