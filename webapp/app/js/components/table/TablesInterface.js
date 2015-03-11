var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Accordion = bs.Accordion;
var Panel = bs.Panel;
var TableAdder = require('./TableAdder.js');
var Table = require('./Table.js');
var TableManager = require('../../TableManager.js');
var TablesInterface = React.createClass({
  tables: function() {
    var tables = TableManager.getTables();
    var components = Object.keys(tables).map(function(name, i) {
      var table = tables[name];
      return (
        <Panel header={name} key={name} eventKey={i}>
          <Table key={name} table={table}/>
        </Panel>
      );
    });
    return <Accordion>{components}</Accordion>;
  },
  addTable: function(table) {
    TableManager.createTable(table);
  },
  adder: function() {
    return (
      <Panel header="Create New Table">
        <TableAdder onSubmit={this.addTable}/>
      </Panel>
    );
  },
  render: function() {
    return (
       <Row>
          <Col xs={3}>{this.adder()}</Col>
          <Col xs={3}>{this.tables()}</Col>
       </Row>
    );
  }
});
module.exports = TablesInterface;
