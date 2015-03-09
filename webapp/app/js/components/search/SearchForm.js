var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var TargetSelector = require('./TargetSelector.js');
var SearchForm = React.createClass({
  render: function() {
    var handleSubmit = this.props.handleSubmit;
    var target = this.props.target;
    var tables = this.props.tables;
    var query = this.props.query;
    var selector = <TargetSelector target={target} tables={tables}/>;
    return (
      <div>
        <form onSubmit={handleSubmit}>
          <Row>
            <Col xs={1}><img src="assets/logo.png" width="65" height="65"/></Col>
            <Col xs={2}>{selector}</Col>
            <Col xs={9}>
              <Input
                type="text"
                placeholder="Enter Query"
                label="Query"
                valueLink={query}/>
            </Col>
          </Row>
        </form>
      </div>
    );
  }
});
module.exports = SearchForm;
