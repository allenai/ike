var xhr = require('xhr');

var tables = {};
var listeners = [];
var userEmail = null;

var TableManager = {
  setUserEmail: function(newUserEmail) {
    if(newUserEmail !== userEmail) {
      tables = {};
      userEmail = newUserEmail;
      this.updateListeners();

      if(userEmail) this.loadTablesFromServer();
    }
  },
  userEmail: function() {
    return userEmail;
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
  },
  hasTable: function(tableName) {
    return tableName in tables;
  },
  createTable: function(table, dontWriteToServer) {
    if(!userEmail) throw "You have to sign in before creating tables.";
    if (!this.hasTable(table.name)) {
      var positive = 'positive' in table ? table.positive : [];
      var negative = 'negative' in table ? table.negative : [];
      tables[table.name] = {
        name: table.name,
        cols: table.cols.slice(0),
        positive: positive,
        negative: negative
      };
      this.updateListeners();
      if(!dontWriteToServer)
        this.writeTableToServer(table.name);
    }
  },
  deleteTable: function(tableName) {
    if(!userEmail) throw "You have to sign in before deleting tables.";
    if (this.hasTable(tableName)) {
      delete tables[tableName];
      this.updateListeners();
      this.deleteTableFromServer(tableName);
    }
  },
  addRow: function(tableName, rowType, row) {
    var hasTable = this.hasTable(tableName);
    var hasRow = this.hasRow(tableName, rowType, row);
    if (hasTable && !hasRow) {
      var rows = tables[tableName][rowType];
      rows.unshift(row);
      this.updateListeners();
      this.writeTableToServer(tableName);
    }
  },
  deleteRow: function(tableName, rowType, row) {
    var rows = tables[tableName][rowType];
    var index = this.getRowIndex(tableName, rowType, row);
    if (index >= 0) {
      rows.splice(index, 1);
      this.updateListeners();
      this.writeTableToServer(tableName);
    }
  },
  getRowIndex: function(tableName, rowType, row) {
    var table = tables[tableName];
    if(!table)
      return -1;

    var rows;
    if(rowType === "positive") {
      rows = table.positive;
    } else if(rowType === "negative") {
      rows = table.negative;
    } else {
      return -1;
    }

    var rowString = function(row) {
      var values = row.values;
      var valueStrings = values.map(TableManager.valueString);
      return valueStrings.join("|");
    };

    return rows.map(rowString).indexOf(rowString(row));
  },
  hasRow: function(tableName, rowType, row) {
    return this.getRowIndex(tableName, rowType, row) >= 0;
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

  deleteTableFromServer: function(tableName) {
    if(!userEmail) throw "You have to sign in before deleting tables.";
    xhr({
      uri: '/api/tables/' + encodeURIComponent(userEmail) + "/" + encodeURIComponent(tableName),
      method: 'DELETE'
    }, function(err, response, body) {
      if(response.statusCode !== 200) {
        console.log("Unexpected response deleting a table: " + JSON.stringify(response));
      }
    });
  },

  writeTableToServer: function(tableName) {
    if(!userEmail) throw "You have to sign in before creating or modifying tables.";
    xhr({
      uri: '/api/tables/' + encodeURIComponent(userEmail) + "/" + encodeURIComponent(tableName),
      method: 'PUT',
      json: tables[tableName]
    }, function(err, response, body) {
      if(response.statusCode !== 200) {
        console.log("Unexpected response writing a table: " + JSON.stringify(response));
      }
    });
  },

  requestTableFromServer: function(tableName) {
    if(!userEmail) throw "You have to sign in before retrieving tables.";
    var self = this;
    xhr({
      uri: '/api/tables/' + encodeURIComponent(userEmail) + "/" + encodeURIComponent(tableName),
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        self.createTable(JSON.parse(body), true);
      } else {
        console.log("Unexpected response requesting a table: " + JSON.stringify(response));
      }
    });
  },

  loadTablesFromServer: function() {
    if(!userEmail) throw "You have to sign in before retrieving tables.";

    // load tables from server
    var self = this;
    xhr({
      uri: '/api/tables/' + encodeURIComponent(userEmail),
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        if(body) {
          body.split("\n").forEach(self.requestTableFromServer.bind(self))
        }
      } else {
        console.log("Unexpected response requesting tables: " + response)
      }
    });
  }
};
module.exports = TableManager;
