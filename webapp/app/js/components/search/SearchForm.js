var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var TargetSelector = require('./TargetSelector.js');
var SuggestQueryButton = require('./SuggestQueryButton.js');
var SearchForm = React.createClass({
  getQueryTextInterface: function(size, query) {
    return <Col xs={size}>
             <Input
               type="text"
               placeholder="Enter Query"
               label="Query"
               valueLink={query}>
             </Input>
           </Col>
  },
  render: function() {
    var handleSubmit = this.props.handleSubmit;
    var target = this.props.target;
    var query = this.props.query;
    var config = this.props.config;
    var selector = <TargetSelector target={target}/>;
    if (config.value.ml.disable) {
      var queryForm =
          <Row>
            <Col xs={2}>{selector}</Col>
            {this.getQueryTextInterface(10, query)}
          </Row>
    } else {
      var queryForm =
          <Row>
            <Col xs={2}>{selector}</Col>
            {this.getQueryTextInterface(8, query)}
            <Col xs={2}>
              <SuggestQueryButton
                config={config}
                target={target}
                query={query}
              ></SuggestQueryButton>
            </Col>
          </Row>
    }
    return (
      <div>
        <form onSubmit={handleSubmit}>
          {queryForm}
        </form>
      </div>
    );
  }
});
module.exports = SearchForm;
