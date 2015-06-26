var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var CorpusSelector = require('../corpora/CorpusSelector.js');
var TargetSelector = require('./TargetSelector.js');
var SuggestQueryButton = require('./SuggestQueryButton.js');
const AuthStore = require('../../stores/AuthStore.js');

var SearchForm = React.createClass({
  propTypes: {
    authenticated: React.PropTypes.bool.isRequired,
    config: React.PropTypes.object.isRequired,
    corpora: React.PropTypes.object.isRequired,
    handleSubmit: React.PropTypes.func.isRequired,
    makeUri: React.PropTypes.func.isRequired,
    query: React.PropTypes.object.isRequired,
    target: React.PropTypes.object.isRequired,
    toggleCorpora: React.PropTypes.func.isRequired
  },
  selectedCorpora: function() {
    return this.props.corpora.value.filter(function(corpus) {
      return corpus.selected;
    });
  },

  render: function() {
    var handleSubmit = this.props.handleSubmit;
    var target = this.props.target;
    var query = this.props.query;
    var config = this.props.config;
    var makeUri = this.props.makeUri;
    var queryWidth = (config.value.ml.disable) ? 10 : 7;
    queryWidth = (this.props.authenticated) ? queryWidth : queryWidth+2;
    var queryForm =
          <Col xs={3}>
            <SuggestQueryButton
              config={config}
              target={target}
              query={query}
              makeUri={makeUri}
              disabled={this.selectedCorpora().length == 0}
            ></SuggestQueryButton>
          </Col>
    return (
      <div>
        <form onSubmit={handleSubmit}>
          <Row>
            {(this.props.authenticated) ? <Col xs={2}><TargetSelector target={target}/></Col> : null}
            <Col xs={queryWidth}>
            <CorpusSelector corpora={this.props.corpora} toggleCorpora={this.props.toggleCorpora}/>
             <Input
               type="text"
               placeholder="Enter Query"
               label="Query"
               valueLink={query}
               disabled={this.selectedCorpora().length == 0}>
             </Input>
           </Col>
            {(config.value.ml.disable) ? null : queryForm}
          </Row>
        </form>
      </div>
    );
  }
});
module.exports = SearchForm;
