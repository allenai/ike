var React = require('react');
var bs = require('react-bootstrap');
var TableManager = require('../../managers/TableManager.js');
var DropdownButton = bs.DropdownButton
var Button = bs.Button
var MenuItem = bs.MenuItem
var Input = bs.Input
var Label = bs.Label
var Badge = bs.Badge
var ButtonGroup = bs.ButtonGroup
var Glyphicon = bs.Glyphicon
var Table = bs.Table
var xhr = require('xhr');

var SuggestQueryButton = React.createClass({

  getInitialState: function() {
    return {
      suggestions: [],
      narrow: false,
      waiting: false
    };
  },

  suggestQueryCallBack: function(err, resp, body) {
    this.setState({waiting: false})
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
    if (this.state.narrow) {
      var scoring = {
        p: config.pWeightNarrow,
        n: config.nWeightNarrow,
        u: config.uWeightNarrow,
      }
    } else {
      var scoring = {
        p: config.pWeight,
        n: config.nWeight,
        u: config.uWeight,
      }
    }
    var requestConfig = {
      depth: config.depth,
      beamSize: config.beamSize,
      maxSampleSize: config.maxSampleSize,
      numEdits: config.numEdits,
      pWeight: scoring.p,
      nWeight: scoring.n,
      uWeight: scoring.u,
      allowDisjunctions: config.allowDisjunctions,
      allowClusters: config.allowClusters
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
      uri: 'api/suggestQuery',
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
    this.setState({waiting: true})
    var request = xhr(requestData, this.suggestQueryCallBack);
  },

  suggestedQuerySelect: function(eventKey, href, target) {
    this.props.query.requestChange("changed")
  },

  createMenuItem: function(scoredQuery) {
    return  (
            <MenuItem
              onSelect={this.suggestedQuerySelect}
              target={scoredQuery.query}>
             {scoredQuery.query}
             &nbsp;
             <span className="labelStrength">
                 <Label bsStyle='success' style={{fontSize: '12'}}>
                    <Glyphicon glyph='plus-sign'/>
                    &nbsp;{scoredQuery.positiveScore.toFixed(2)}
                 </Label>&nbsp;
                 <Label bsStyle='danger' style={{fontSize: '12'}}>
                    <Glyphicon glyph='minus-sign'/>
                    &nbsp;{scoredQuery.negativeScore.toFixed(2)}
                 </Label>&nbsp;
                 <Label style={{fontSize: '12'}}>
                    <Glyphicon glyph='question-sign'/>&nbsp;
                    {scoredQuery.unlabelledScore.toFixed(2)}
                 </Label>
              </span>
            </MenuItem>
   )
  },

  numberString: function(number) {
    if (number >= 10000) {
      return (number/1000) + "k"
    } else {
      return number
    }
  },

  checkBoxChange: function(event) {
    this.setState({narrow: !this.state.narrow})
  },

  buildTableRow: function(scoredQuery, scoreScale, unlabelledScale) {
    var query = this.props.query
    function clicked() {
      query.requestChange(scoredQuery.query)
    }

    return (
      <tr onClick={clicked} target={scoredQuery.query}>
        <td className="queryCell">{scoredQuery.query}</td>
        <td className="queryCell queryStat">
          {(scoredQuery.positiveScore * scoreScale).toFixed(2)}</td>
        <td className="queryCell queryStat">
          {(scoredQuery.negativeScore * scoreScale).toFixed(2)}
        </td>
        <td className="queryCell queryStat">
          {(scoredQuery.unlabelledScore * unlabelledScale).toFixed(2)}
        </td>
      </tr>
    )
  },

  render: function() {
    var maxLabelledScore = 0
    var maxUnlabelledScore = 0
    var arrayLength =  this.state.suggestions.length;
    for (var i = 0; i < arrayLength; i++) {
        var suggestion = this.state.suggestions[i]
        maxLabelledScore = Math.max(maxLabelledScore, suggestion.positiveScore, suggestion.negativeScore)
        maxUnlabelledScore = Math.max(maxUnlabelledScore, suggestion.unlabelledScore)
    }
    var scoredScale = 1
    var unlabelledScale = 1
    while (maxLabelledScore * scoredScale < 0.1 && maxLabelledScore > 0) {
        scoredScale *= 10
    }
    while (maxUnlabelledScore * unlabelledScale < 1 && maxUnlabelledScore > 0) {
        unlabelledScale *= 10
    }
    var rows = []
    for (var i = 0; i < arrayLength; i++) {
        var suggestion = this.state.suggestions[i]
        rows.push(this.buildTableRow(suggestion, scoredScale, unlabelledScale))
    }

    var scoredScaleStr = this.numberString(scoredScale)
    var unlabelledScaleStr = this.numberString(unlabelledScale)

    if (scoredScale == 1) {
      var positiveLabel = <div>Positive<br/>Probability</div>
      var negativeLabel = <div>Negative<br/>Probability</div>
    } else {
      var positiveLabel = <div>Positives<br/>(Per {scoredScale} Docs)</div>
      var negativeLabel = <div>Negatives<br/>(Per {scoredScale} Docs)</div>
    }


    var tableInstance = (
      <Table
       condensed
       bordered
       id="suggestion-table"
       hover>
        <thead>
          <tr>
            <th className="queryHeader queryCell">Query</th>
            <th className="queryHeader queryCell">{positiveLabel}</th>
            <th className="queryHeader queryCell">{negativeLabel}</th>
            <th className="queryHeader queryCell">Matches per<br/>{unlabelledScaleStr} Docs</th>
          </tr>
        </thead>
        <tbody>
          {rows}
        </tbody>
      </Table>)

    return (
    <div>
      <label className="control-label">Query</label>
      <div>
            <ButtonGroup id="suggestion-menu">
          <DropdownButton
            style={{fontSize: 'small'}}
            pullRight
            title="Suggestions">
              {tableInstance}
          </DropdownButton>
          <Button
            style={{fontSize: 'small'}}
            disabled={this.state.waiting}
            onClick={this.suggestQuery}
            >Refresh
          </Button>
        </ButtonGroup>
        <Input
          type='checkbox'
          style={{fontSize: 'small'}}
          label='Narrow'
          onChange={this.checkBoxChange}
          defaultChecked={this.state.narrow}/>
      </div>
    </div>
    );
  }
});
module.exports = SuggestQueryButton;
