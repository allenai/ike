var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Accordion = bs.Accordion;
var Panel = bs.Panel;
var TableAdder = require('./TableAdder.js');
var Table = require('./Table.js');
var TableManager = require('../../managers/TableManager.js');
var DeleteButton = require('../misc/DeleteButton.js');
var TablesInterface = React.createClass({
  tables: function() {
    var tables = TableManager.getTables();
    var components = Object.keys(tables).map(function(name, i) {
      var button = <DeleteButton callback={this.deleteTable(name)}/>;
      var header = <span>{name} {button}</span>;
      var table = tables[name];
      return (
        <Panel style={{height: 'auto'}} header={header} key={name} eventKey={i}>
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
  deleteTable: function(tableName) {
    return function() {
      TableManager.deleteTable(tableName);
    };
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
          <Col xs={7}>{this.tables()}</Col>
       </Row>
    );
  }
});
module.exports = TablesInterface;
