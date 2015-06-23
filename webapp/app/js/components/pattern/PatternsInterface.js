var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Alert = bs.Alert;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Row = bs.Row;
var Col = bs.Col;

var PatternsInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  getInitialState: function() {
    return {
      error: null,
      patterns: [],
      activePatternName: null
    };
  },

  componentDidMount: function() {
    var self = this;

    // Get the patterns from the server
    xhr({
      uri: '/api/patterns',
      method: 'GET'
    }, function(err, resp, body) {
      if(resp.statusCode == 200) {
        // set patterns
        var patternsObjects = JSON.parse(body);
        var patterns = {};
        patternsObjects.forEach(function(patternObject) {
          patterns[patternObject.name] = patternObject.pattern;
        });

        self.setState({patterns: patterns});

        // set active pattern name
        var activePatternName = self.state.activePatternName;
        if(activePatternName && !patterns.hasOwnProperty(activePatternName))
          activePatternName = null;

        if(!activePatternName)
          activePatternName = patternsObjects.length > 0 ? patternsObjects[0].name : null;

        self.setState({activePatternName: activePatternName});
      } else {
        self.setState({error: resp.body + " (" + resp.statusCode + ")"});
        console.log(resp);
      }
    });
  },

  patternClicked: function(patternName) {
    this.setState({activePatternName: patternName});
  },

  render: function() {
    if(this.state.error) {
      return <Alert bsStyle="danger">{this.state.error}</Alert>
    } else {
      var self = this;
      var items = [];
      for(var patternName in this.state.patterns) {
        if(this.state.patterns.hasOwnProperty(patternName)) {
          items.push(
            <NavItem key={patternName} eventKey={patternName}>
            {patternName}
            </NavItem>);
        }
      }

      var patternChooser = <Nav stacked bsStyle='pills'
                                activeKey={this.state.activePatternName}
                                onSelect={this.patternClicked}>{items}</Nav>;
      
      return <Row>
        <Col xs={3}>{patternChooser}</Col>
        <Col xs={7}></Col>
      </Row>;
    }
  }
});

module.exports = PatternsInterface;
