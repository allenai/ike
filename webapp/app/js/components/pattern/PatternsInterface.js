var React = require('react/addons');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Alert = bs.Alert;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var PatternEditor = require('./PatternEditor.js');

var PatternsInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    corpora: React.PropTypes.object.isRequired,
  },

  getInitialState: function() {
    return {
      error: null,
      patterns: [],
      activePatternName: null,
      newPatternName: ""
    };
  },

  componentDidMount: function() {
    var self = this;

    // Get the patterns from the server
    xhr({
      uri: '/api/patterns',
      method: 'GET'
    }, function(err, resp, body) {
      if (self.isMounted()) {
        if (resp.statusCode == 200) {
          // set patterns
          var patternsObjects = JSON.parse(body);
          var patterns = {};
          patternsObjects.forEach(function (patternObject) {
            patterns[patternObject.name] = patternObject.pattern;
          });

          // set active pattern name
          var activePatternName = self.state.activePatternName;
          if (activePatternName && !patterns.hasOwnProperty(activePatternName))
            activePatternName = null;

          if (!activePatternName)
            activePatternName = patternsObjects.length > 0 ? patternsObjects[0].name : null;

          self.setState({
            patterns: patterns,
            activePatternName: activePatternName,
            error: null
          });
        } else {
          self.setState({error: resp.body + " (" + resp.statusCode + ")"});
          console.log(resp);
        }
      }
    });
  },

  patternClicked: function(patternName) {
    this.setState({activePatternName: patternName});
  },

  newPatternNameValidationState: function() {
    var result = "success";
    var newPatternName = this.state.newPatternName;
    if(!newPatternName)
      result = null;
    else if(newPatternName.includes(" ") || this.state.patterns.hasOwnProperty(newPatternName))
      result = "error";

    return result;
  },

  createPattern: function() {
    if(this.newPatternNameValidationState() === "success") {
      var patterns = this.state.patterns;
      patterns[this.state.newPatternName] = "";
      this.setState({
        patterns: patterns,
        activePatternName: this.state.newPatternName,
        newPatternName: ""
      });
    }
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

      var patternChooser = <div>
        <Nav stacked bsStyle='pills'
             activeKey={this.state.activePatternName}
             onSelect={this.patternClicked}>
          {items}
        </Nav>
        <hr/>
        <form onSubmit={this.createPattern}>
          <Input type='text'
                 placeholder='Pattern Name'
                 label='Add a new pattern'
                 help='Enter a name for a new pattern and press enter.'
                 valueLink={this.linkState('newPatternName')}
                 bsStyle={this.newPatternNameValidationState()}
                 hasFeedback />
        </form>
        </div>;

      var patternEditor = <div/>;
      if(this.state.activePatternName) {
        var activeQuery = this.state.patterns[this.state.activePatternName];
        patternEditor = <PatternEditor patternName={this.state.activePatternName}
                                       initialQuery={activeQuery}
                                       corpora={this.props.corpora}/>;
      }
      return <Row>
        <Col md={2}>{patternChooser}</Col>
        <Col md={10}>{patternEditor}</Col>
      </Row>;
    }
  }
});

module.exports = PatternsInterface;
