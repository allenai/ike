var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var ConfigInterface = React.createClass({
  toggleCheckbox: function(configName) {
    var config = this.props.config;
    return function(e) {
      config.value[configName] = !config.value[configName];
      config.requestChange(config.value);
    }
  },
  onChange: function(configName, transform) {
    var config = this.props.config;
    return function(e) {
      config.value[configName] = transform(e.target.value);
      console.log('setting ' + configName + ' to ' + transform(e.target.value));
      config.requestChange(config.value);
    };
  },
  render: function() {
    var config = this.props.config.value;
    var hideAddedChange = this.toggleCheckbox('hideAdded');
    var limitChange = this.onChange('limit', parseInt);
    var eLimitChange = this.onChange('evidenceLimit', parseInt);
    return (
      <div>
        <Input
          type="checkbox"
          label="Hide rows that are in dictionary"
          checked={config.hideAdded}
          onChange={hideAddedChange}/>
        <Input
          type="select"
          label="Max Rows"
          onChange={limitChange}
          value={config.limit}>
          <option value="10">10</option>
          <option value="100">100</option>
          <option value="200">200</option>
          <option value="500">500</option>
          <option value="1000">1000</option>
          <option value="10000">10000</option>
        </Input>
        <Input
          type="select"
          label="Evidence Per Row"
          onChange={eLimitChange}
          value={config.evidenceLimit}>
          <option value="1">1</option>
          <option value="5">5</option>
          <option value="10">10</option>
          <option value="100">100</option>
          <option value="1000">1000</option>
          <option value="10000">10000</option>
        </Input>
      </div>
    );
  }
});
module.exports = ConfigInterface;
