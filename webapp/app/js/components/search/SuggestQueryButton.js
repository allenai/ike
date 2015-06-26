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
var Loader = require('react-loader');
var xhr = require('xhr');


var SuggestQueryButton = React.createClass({
  getInitialState: function() {
    return {
      suggestions: [],
      sampleSize: 0,
      disabled: false,
      waiting: false,
      error: null,
      suggestionsFor: null
    };
  },

  componentDidMount: function() {
    TableManager.addChangeListener(this.tableChanged)
  },

  componentWillUnmount: function(){
    TableManager.removeChangeListener(this.tableChanged)
  },

  tableChanged: function(table) {
    // So the suggestions will be refreshed if a table is updated
    this.setState({suggestionsFor: null})
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
      console.log("Suggest query server error: " + resp.body)
      this.setState({error: 'Server Error'});
    }
  },

  suggestQuery: function() {
    if (this.state.waiting) {
      return;
    }

    var targetValue = this.props.target.value;
    if (targetValue === null) {
      this.setState({error: 'Select a Table'});
      return;
    }

    var queryValue = this.props.query.value;
    if (queryValue === null) {
      this.setState({error: 'Enter a starting query'});
      return;
    }

    var config = this.props.config.value.ml
    if (this.props.narrow) {
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
      uWeight: scoring.u
    }

    // We store the URI so we refresh if new corpra are added
    var uri = this.props.makeUri('suggestQuery');
    var suggestingFor = {query: queryValue, uri: uri, config: requestConfig, target: targetValue};
    if (JSON.stringify(this.state.suggestionsFor) == JSON.stringify(suggestingFor)) {
      return;
    }

    var tables = TableManager.getTables()
    var requestData = {
      body: JSON.stringify({
        query: queryValue,
        tables: tables,
        target: targetValue,
        narrow: this.props.narrow,
        config: requestConfig
      }),
      uri: uri,
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
    this.setState({error: null, suggestionsFor: suggestingFor, waiting: true});
    var request = xhr(requestData, this.suggestQueryCallBack);
  },

  buildTableRow: function(scoredQuery, key) {

    var clicked = function clicked(event, target, href) {
      // Stop the click reaching the <div> around the dropdown
      event.stopPropagation();
      this.props.query.requestChange(scoredQuery.query);
      this.props.submitQuery(event);
      this.refs.dropDown.setDropdownState(false);
    }.bind(this);

    return (
      <tr className="queryRow" onClick={clicked} target={scoredQuery.query} key={key}>
        <td className="queryCell">{scoredQuery.query}</td>
        <td className="queryCell queryStat">
          {scoredQuery.positiveScore.toFixed(0)}</td>
        <td className="queryCell queryStat">
          {scoredQuery.negativeScore.toFixed(0)}
        </td>
        <td className="queryCell queryStat">
          {scoredQuery.unlabelledScore .toFixed(2)}
        </td>
      </tr>
    )
  },

  render: function() {
    var title;
    if (this.props.narrow) {
      title = "Narrow";
    } else {
      title = "Broaden";
    }

    var instanceToShow;
    if (this.state.waiting) {
      instanceToShow =
        <div style={{height: "35px"}}>
          <Loader scale={0.70}/>
        </div>
    } else if (this.state.error != null) {
      instanceToShow = (<div style={{textAlign: 'center', fontStyle: 'italic'}}>
        {"(" + this.state.error + ")"}</div>)
    } else {
      var rows = []
      for (var i = 0; i < this.state.suggestions.length; i++) {
          rows.push(this.buildTableRow(this.state.suggestions[i], i))
      }

      instanceToShow =
        <Table
         condensed
         bordered
         id="suggestion-table"
         hover>
          <thead>
            <tr>
              <th className="queryHeader">{"Query (Sample Size: " +
                  Math.max((this.state.sampleSize * 100).toFixed(2), 0.01) + "%)"}</th>
              <th className="queryHeader">Positive Rows</th>
              <th className="queryHeader">Negative Rows</th>
              <th className="queryHeader">(Approximate) Unlabelled Rows</th>
            </tr>
          </thead>
          <tbody>
            {rows}
          </tbody>
        </Table>
     }


    return (
    <div>
      <div>
        <ButtonGroup>
          <div onClick={this.suggestQuery}>
          <DropdownButton
            bsSize='small'
            pullRight
            ref="dropDown"
            title={title}>
              {instanceToShow}
          </DropdownButton>
          </div>
        </ButtonGroup>
      </div>
    </div>
    );
  }
});
module.exports = SuggestQueryButton;
