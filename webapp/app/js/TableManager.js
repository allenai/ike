var tables = {};
var listeners = [];
var rowIndex = {};
TableManager = {
  rowId: function(tableName, rowType, row) {
    var rowString = this.rowString(row);
    var fields = [tableName, rowType, rowString];
    return fields.join(".");
  },
  rowString: function(row) {
    var values = row.values;
    var valueStrings = values.map(this.valueString);
    return valueStrings.join("|");
  },
  valueString: function(value) {
    var qwords = value.qwords;
    var words = qwords.map(function(qw) { return qw.value; });
    return words.join(" ");
  },
  getTables: function() {
    return tables;
  },
  getRows: function(tableName, rowType) {
    if (this.hasTable(tableName)) {
      return tables[tableName][rowType];
    } else {
      return [];
    }
  },
  addChangeListener: function(listener) {
    listeners.push(listener);
  },
  removeChangeListener: function(listener) {
    var index = listeners.indexOf(listener);
    if (index !== -1) {
      listeners.splice(listener, 1);
    }
  },
  updateListeners: function() {
    listeners.map(function(listener) {
      listener(tables);
    });
  },
  hasTable: function(tableName) {
    return tableName in tables;
  },
  createTable: function(table) {
    if (!this.hasTable(table.name)) {
      tables[table.name] = {
        name: table.name,
        cols: table.cols.slice(0),
        positive: [],
        negative: []
      };
      this.updateListeners();
    }
  },
  deleteTable: function(tableName) {
    var posRows = this.getRows(tableName, "positive");
    var negRows = this.getRows(tableName, "negative");
    if (this.hasTable(tableName)) {
      delete tables[tableName];
      this.updateListeners();
    }
    posRows.map(this.removeRowIndex);
    negRows.map(this.removeRowIndex);
  },
  addRow: function(tableName, rowType, row) {
    var hasTable = this.hasTable(tableName);
    var hasRow = this.hasRow(tableName, rowType, row);
    if (hasTable && !hasRow) {
      var rows = tables[tableName][rowType];
      rows.unshift(row);
      var id = this.rowId(tableName, rowType, row);
      rowIndex[id] = true;
      this.updateListeners();
    }
  },
  deleteRow: function(tableName, rowType, row) {
    if (this.hasRow(tableName, rowType, row)) {
      var rows = tables[tableName][rowType];
      var index = rows.indexOf(row);
      rows.splice(index, 1);
      this.updateListeners();
      var rowId = this.rowId(tableName, rowType, row);
      this.removeRowIndex(tableName, rowType, row);
    }
  },
  removeRowIndex: function(tableName, rowType, row) {
    var id = this.rowId(tableName, rowType, row);
    if (id in rowIndex) {
      delete rowIndex[id];
    }
  },
  hasRow: function(tableName, rowType, row) {
    var rowId = this.rowId(tableName, rowType, row);
    return rowId in rowIndex;
  },
  hasPositiveRow: function(tableName, row) {
    return this.hasRow(tableName, "positive", row);
  },
  hasNegativeRow: function(tableName, row) {
    return this.hasRow(tableName, "negative", row);
  },
};
module.exports = TableManager;
