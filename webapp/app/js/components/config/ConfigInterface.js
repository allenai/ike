var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Panel = bs.Panel;
var PanelGroup = bs.PanelGroup;
var Well = bs.Well;
var Corpora = require('../corpora/Corpora.js');
var ConfigInterface = React.createClass({
  toggleCheckbox: function(configName, subconfig) {
    var config = this.props.config;
    return function(e) {
      if (!subconfig) {
        config.value[configName] = !config.value[configName];
      } else {
        config.value[subconfig][configName] = !config.value[subconfig][configName];
      }
      console.log('toggling '+ configName + ' to ' + config.value[subconfig][configName]);
      config.requestChange(config.value);
    }
  },
  onChange: function(configName, transform, subconfig) {
    var config = this.props.config;
    return function(e) {
      if (!subconfig) {
        config.value[configName] = transform(e.target.value);
      } else {
        config.value[subconfig][configName] = transform(e.target.value);
      }
      console.log('setting ' + configName + ' to ' + transform(e.target.value));
      config.requestChange(config.value);
    };
  },
  render: function() {
    var config = this.props.config.value;
    var hideAddedChange = this.toggleCheckbox('hideAdded');
    var limitChange = this.onChange('limit', parseInt);
    var eLimitChange = this.onChange('evidenceLimit', parseInt);
    var depthChange = this.onChange('depth', parseInt, 'ml')
    var beamSizeChange = this.onChange('beamSize', parseInt, 'ml')
    var maxSampleSizeChange = this.onChange('maxSampleSize', parseInt, 'ml')
    var pWeightChange = this.onChange('pWeight', parseFloat, 'ml')
    var nWeightChange = this.onChange('nWeight', parseFloat, 'ml')
    var uWeightChange = this.onChange('uWeight', parseFloat, 'ml')
    var pWeightChangeNarrow = this.onChange('pWeightNarrow', parseFloat, 'ml')
    var nWeightChangeNarrow = this.onChange('nWeightNarrow', parseFloat, 'ml')
    var uWeightChangeNarrow = this.onChange('uWeightNarrow', parseFloat, 'ml')
    var allowDisjunctionChange = this.toggleCheckbox('allowDisjunctions', 'ml')
    var disable = this.toggleCheckbox('disable', 'ml')
    return (
      <div>
        <PanelGroup>
          <Panel header='Corpora to Query'>
            <Corpora corpora={this.props.corpora} toggleCorpora={this.props.toggleCorpora} />
          </Panel>
        </PanelGroup>
        <Input
          type='checkbox'
          label='Hide rows that are in table'
          checked={config.hideAdded}
          onChange={hideAddedChange}/>
        <Input
          type='select'
          label='Max Rows'
          onChange={limitChange}
          value={config.limit}>
          <option value='10'>10</option>
          <option value='100'>100</option>
          <option value='200'>200</option>
          <option value='500'>500</option>
          <option value='1000'>1000</option>
          <option value='10000'>10000</option>
        </Input>
        <Input
          type='select'
          label='Evidence Per Result Group'
          onChange={eLimitChange}
          value={config.evidenceLimit}>
          <option value='1'>1</option>
          <option value='5'>5</option>
          <option value='10'>10</option>
          <option value='100'>100</option>
          <option value='1000'>1000</option>
          <option value='10000'>10000</option>
        </Input>
        <PanelGroup accordion>
            <Panel header='Query Suggestion Settings' collapsed='True'>
              <Input
                type='checkbox'
                label='Disable'
                checked={config.ml.disable}
                onChange={disable}>
              </Input>
              <Input
                type='checkbox'
                label='Suggest Disjunctions'
                checked={config.ml.allowDisjunctions}
                onChange={allowDisjunctionChange}>
              </Input>
              <Input
                  type='select'
                  value={config.ml.depth}
                  onChange={depthChange}
                  label='Max Number of Edits'>
                  <option value='1'>1</option>
                  <option value='2'>2</option>
                  <option value='3'>3</option>
                  <option value='4'>4</option>
                  <option value='5'>5</option>
                </Input>
                <Input
                  type='range'
                  label={'Beam Size (' + config.ml.beamSize + ')'}
                  onChange={beamSizeChange}
                  value={config.ml.beamSize}
                  min='1'
                  max='500'>
                </Input>
               <Input
                  type='number'
                  label='Sample Size'
                  onChange={maxSampleSizeChange}
                  value={config.ml.maxSampleSize}
                  min='1'>
                </Input>
                <PanelGroup accordion>
                <Panel header='Broaden Scoring' collapsed='True'>
                <Input
                    type='range'
                    label={'Positive Weight ' + config.ml.pWeight.toFixed(2)}
                    value={config.ml.pWeight}
                    onChange={pWeightChange}
                    step='0.2'
                    min='-5'
                    max='5'>
                </Input>
                <Input
                    type='range'
                    label={'Negative Weight ' + config.ml.nWeight.toFixed(2)}
                    value={config.ml.nWeight}
                    onChange={nWeightChange}
                    step='0.2'
                    min='-5'
                    max='5'>
                </Input>
                <Input
                    type='range'
                    label={'Unlabelled Weight ' + config.ml.uWeight.toFixed(2)}
                    value={config.ml.uWeight}
                    onChange={uWeightChange}
                    step='0.001'
                    min='-1'
                    max='1'>
                </Input>
                </Panel>
                <Panel header='Narrow Scoring' collapsed='True'>
                <Input
                    type='range'
                    label={'Positive Weight ' + config.ml.pWeightNarrow.toFixed(2)}
                    value={config.ml.pWeightNarrow}
                    onChange={pWeightChangeNarrow}
                    step='0.2'
                    min='-5'
                    max='5'>
                </Input>
                <Input
                    type='range'
                    label={'Negative Weight ' + config.ml.nWeightNarrow.toFixed(2)}
                    value={config.ml.nWeightNarrow}
                    onChange={nWeightChangeNarrow}
                    step='0.2'
                    min='-5'
                    max='5'>
                </Input>
                <Input
                    type='range'
                    label={'Unlabelled Weight ' + config.ml.uWeightNarrow.toFixed(2)}
                    value={config.ml.uWeightNarrow}
                    onChange={uWeightChangeNarrow}
                    step='0.001'
                    min='-1'
                    max='1'>
                </Input>
                </Panel>
                </PanelGroup>
            </Panel>
        </PanelGroup>
      </div>
    );
  }
});
module.exports = ConfigInterface;
