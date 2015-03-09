var valueString = function(tableValue) {
  var qwords = tableValue.qwords;
  var words = qwords.map(function(qw) { return qw.value; });
  return words.join(" ");
};
var rowString = function(tableRow) {
  var values = tableRow.values;
  var strings = values.map(valueString);
  return strings.join("|");
};
// TODO: remove this, only used for transition from dicts -> tables
var stringToRow = function(string) {
  var words = string.split(" ");
  var qwords = words.map(function(w) { return {value: w}; });
  var value = {qwords: qwords};
  var row = {values: [value]};
  return row;
};
module.exports = {
  valueString: valueString,
  rowString: rowString,
  stringToRow: stringToRow
};
