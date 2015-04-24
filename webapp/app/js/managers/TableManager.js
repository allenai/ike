var xhr = require('xhr');

var tables = {};
var listeners = [];
var rowIndex = {};

var TableManager = {

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
  rowStrings: function(rows) {
    return rows.map(this.rowString);
  },
  valueString: function(value) {
    var qwords = value.qwords;
    var words = qwords.map(function(qw) { return qw.value; });
    return words.join(" ");
  },
  stringValue: function(string) {
    var words = string.split(" ");
    var qwords = words.map(function(w) { return {value: w}; });
    return {qwords: qwords};
  },
  stringsRow: function(strings) {
    var values = strings.map(this.stringValue);
    return {values: values};
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
    // TODO this.saveTablesToLocalStorage();
  },
  hasTable: function(tableName) {
    return tableName in tables;
  },
  createTable: function(table) {
    if (!this.hasTable(table.name)) {
      var positive = 'positive' in table ? table.positive : [];
      var negative = 'negative' in table ? table.negative : [];
      tables[table.name] = {
        name: table.name,
        cols: table.cols.slice(0),
        positive: positive,
        negative: negative
      };
      positive.map(function(row) {
        this.addRowIndex(table.name, 'positive', row);
      }.bind(this));
      negative.map(function(row) {
        this.addRowIndex(table.name, 'negative', row);
      }.bind(this));
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
  addRowIndex: function(tableName, rowType, row) {
    var id = this.rowId(tableName, rowType, row);
    rowIndex[id] = true;
  },
  addRow: function(tableName, rowType, row) {
    var hasTable = this.hasTable(tableName);
    var hasRow = this.hasRow(tableName, rowType, row);
    if (hasTable && !hasRow) {
      var rows = tables[tableName][rowType];
      rows.unshift(row);
      this.addRowIndex(tableName, rowType, row);
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
  toggleRow: function(tableName, rowType, row) {
    if (this.hasRow(tableName, rowType, row)) {
      this.deleteRow(tableName, rowType, row);
    } else {
      this.addRow(tableName, rowType, row);
    }
  },

  labeledRowStrings: function(table, rowType) {
    var appendLabelToRow = function(row) {
      var values = row.values;
      var strings = values.map(this.valueString);
      strings.push(rowType);
      strings.push(JSON.stringify(row.provenance));
      return strings;
    }.bind(this);
    return table[rowType].map(appendLabelToRow);
  },
  table2csv: function(table) {
    // Get string arrays representing the rows, with a label column added
    // to the end.
    var posRows = this.labeledRowStrings(table, 'positive');
    var negRows = this.labeledRowStrings(table, 'negative');
    var allRows = posRows.concat(negRows);
    // Add a header row with column names.
    var headerRow = table.cols.slice(0);
    headerRow.push('label');
    headerRow.push('provenance');
    allRows.unshift(headerRow);
    // Return as a string.
    return allRows.map(function(row) {
      return row.join("\t");
    }).join("\n");
  },

  makeTablePutRequest: function(table) {
    return {
      json: table,
      uri: '/api/tables/' + table.name,
      method: 'PUT'
    };
  },

  requestTableFromServer: function(tableName) {
    var self = this;
    xhr({
      uri: '/api/tables/' + tableName,
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        self.createTable(JSON.parse(body));
      } else {
        console.log("Unexpected response requesting a table: " + response);
      }
    });
  },

  loadTablesFromServer: function() {
    // delete old table storage from the browser
    localStorage.removeItem('tables')

    // load tables from server
    var self = this;
    xhr({
      uri: '/api/tables',
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        body.split("\n").forEach(self.requestTableFromServer.bind(self))
      } else {
        console.log("Unexpected response requesting tables: " + response)
      }
    });
  },

};
module.exports = TableManager;
