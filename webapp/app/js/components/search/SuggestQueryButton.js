var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var DropdownButton = bs.DropdownButton
var MenuItem = bs.MenuItem
var Input = bs.Input
var SplitButton = bs.SplitButton
var ButtonGroup = bs.ButtonGroup
var xhr = require('xhr');

var SuggestQueryButton = React.createClass({

  getInitialState: function() {
    return {
      suggestions: [],
      narrow: false,
    };
  },

  suggestQueryCallBack: function(err, resp, body) {
    if (resp.statusCode == 200) {
      var newSuggestions = JSON.parse(body).scoredQueries
      this.setState({suggestions: newSuggestions})
    } else {
      alert('Got Error: ' + err + '\n<' + body + '>')
    }
  },

  suggestQuery: function() {
    var targetValue = this.props.target.value;
    if (targetValue === null) {
      alert('A target table must be set to use this feature')
      return;
    }

    var queryValue = this.props.query.value;
    if (queryValue === null) {
      alert('Enter a starting query');
      return;
    }

    var config = this.props.config.value.ml
    var requestConfig = {
      depth: config.depth,
      beamSize: config.beamSize,
      maxSampleSize: config.maxSampleSize,
      numEdits: config.numEdits,
      pWeight: config.pWeight,
      nWeight: config.nWeight,
      uWeight: config.uWeight,
      allowDisjunctions: config.allowDisjunctions
    }

    var tables = TableManager.getTables()

    var requestData = {
      body: JSON.stringify({
        query: queryValue,
        tables: tables,
        target: targetValue,
        narrow: this.state.narrow,
        config: requestConfig
      }),
      uri: '/api/suggestQuery',
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
    console.log('Requesting suggestions for ' + queryValue)
    var request = xhr(requestData, this.suggestQueryCallBack);
  },

  suggestedQuerySelect: function(eventKey, href, target) {
    this.props.query.requestChange(target)
  },

  createMenuItem: function(scoredQuery) {
    return  (
            <MenuItem
              onSelect={this.suggestedQuerySelect}
              target={scoredQuery.query}>
              {scoredQuery.query}
            </MenuItem>
   )
  },

  checkBoxChange: function(event) {
    this.setState({narrow: !this.state.narrow})
  },

  render: function() {
    return (
    <div>
      <ButtonGroup>
        <SplitButton title='Suggest Query' onClick={this.suggestQuery} pullRight>
          {this.state.suggestions.map(this.createMenuItem)}
        </SplitButton>
      </ButtonGroup>
      <Input
        type='checkbox'
        label='Narrow'
        onChange={this.checkBoxChange}
        defaultChecked={this.state.narrow}/>
    </div>
    );
  }
});
module.exports = SuggestQueryButton;
