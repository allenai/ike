var React = require('react');
var bs = require('react-bootstrap');
var SuggestQueryButton = require('./SuggestQueryButton.js');
var Label = bs.Label
var Row = bs.Row;
var Col = bs.Col;
var ButtonToolbar = bs.ButtonToolbar;

var SuggestQueryButtonGroup = React.createClass({

  render: function() {
    var props = this.props;
    var config = props.config;
    var target = props.target;
    var query = props.query;
    var makeUri = props.makeUri;
    var submitQuery = props.submitQuery;
    var disabled = props.disabled;
    return (
    <div stye={{display: "inlineBlock"}}>
       <label className="control-label">Suggestions</label>
       <ButtonToolbar>
           <SuggestQueryButton
             config={config}
             target={target}
             query={query}
             makeUri={makeUri}
             submitQuery={submitQuery}
             narrow={true}
             disabled={disabled}
           ></SuggestQueryButton>
           <SuggestQueryButton
             config={config}
             target={target}
             query={query}
             makeUri={makeUri}
             submitQuery={submitQuery}
             narrow={false}
             disabled={disabled}
           ></SuggestQueryButton>
       </ButtonToolbar>
    </div>)
  }
});
module.exports = SuggestQueryButtonGroup;