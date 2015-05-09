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
var Table = bs.Table
var xhr = require('xhr');

var SuggestQueryButton = React.createClass({

  getInitialState: function() {
    return {
      suggestions: [],
      sampleSize: 0,
      narrow: false,
      waiting: false
    };
  },

  suggestQueryCallBack: function(err, resp, body) {
    this.setState({waiting: false})
    if (resp.statusCode == 200) {
      var suggestions = JSON.parse(body)
      suggestions.suggestions.unshift(suggestions.original)
      this.setState({
        suggestions: suggestions.suggestions,
        sampleSize: suggestions.samplePercent
      })
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
      allowDisjunctions: config.allowDisjunctions
    }

    var tables = TableManager.getTables()
    var uri = this.props.makeUri('suggestQuery');
    var requestData = {
      body: JSON.stringify({
        query: queryValue,
        tables: tables,
        target: targetValue,
        narrow: this.state.narrow,
        config: requestConfig
      }),
      uri: uri,
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
    this.setState({waiting: true})
    var request = xhr(requestData, this.suggestQueryCallBack);
  },

  suggestedQuerySelect: function(eventKey, href, target) {
    this.props.query.requestChange("changed")
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

  buildTableRow: function(scoredQuery) {
    var query = this.props.query
    function clicked() {
      query.requestChange(scoredQuery.query)
    }

    return (
      <tr className="queryRow" onClick={clicked} target={scoredQuery.query}>
        <td className="queryCell">{scoredQuery.query}</td>
        <td className="queryCell queryStat">
          {scoredQuery.positiveScore.toFixed(2)}</td>
        <td className="queryCell queryStat">
          {scoredQuery.negativeScore.toFixed(2)}
        </td>
        <td className="queryCell queryStat">
          {scoredQuery.unlabelledScore .toFixed(2)}
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
    var labelledScoreScale = 1
    var unlabelledScoreScale = 1
    while (maxLabelledScore * labelledScoreScale < 0.1 && maxLabelledScore > 0) {
        labelledScoreScale *= 10
    }
    while (maxUnlabelledScore * unlabelledScoreScale < 1 && maxUnlabelledScore > 0) {
        unlabelledScoreScale *= 10
    }
    var rows = []
    for (var i = 0; i < arrayLength; i++) {
        var suggestion = this.state.suggestions[i]
        rows.push(this.buildTableRow(suggestion))
    }

    var tableInstance = (
      <Table
       condensed
       bordered
       id="suggestion-table"
       hover>
        <thead>
          <tr>
            <th className="queryHeader">{"Query (Sample Size: " +
                Math.max((this.state.sampleSize * 100).toFixed(2), 0.01) + "%)"}</th>
            <th className="queryHeader">Positive Score</th>
            <th className="queryHeader">Negative Score</th>
            <th className="queryHeader">Unlabelled Score</th>
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
         <ButtonGroup>
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
