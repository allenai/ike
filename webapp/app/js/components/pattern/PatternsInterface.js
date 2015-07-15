var React = require('react/addons');
var bs = require('react-bootstrap');
var Alert = bs.Alert;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var Well = bs.Well;
var PatternEditor = require('./PatternEditor.js');
const PatternsStore = require('../../stores/NamedPatternsStore.js');
const AuthStore = require('../../stores/AuthStore.js');

var PatternsInterface = React.createClass({
  mixins: [React.addons.LinkedStateMixin],

  propTypes: {
    config: React.PropTypes.object.isRequired
  },

  getInitialState: function() {
    return {
      error: null,
      patterns: {},
      activePatternName: null,
      newPatternName: ""
    };
  },

  updatePatterns: function() {
    var error = PatternsStore.getError();
    if(error) {
      this.setState({
        error: error,
        patterns: {},
        activePatternName: null
      });
    } else {
      var newPatterns = PatternsStore.getPatterns();

      // set active pattern name
      var activePatternName = this.state.activePatternName;
      if (activePatternName && !newPatterns.hasOwnProperty(activePatternName))
        activePatternName = null;

      var patternNames = [];
      for(var patternName in newPatterns) {
        if(newPatterns.hasOwnProperty(patternName))
          patternNames.push(patternName);
      }

      if (!activePatternName)
        activePatternName = patternNames.length > 0 ? patternNames[0] : null;

      this.setState({
        error: null,
        patterns: newPatterns,
        activePatternName: activePatternName
      });
    }
  },

  componentDidMount: function() {
    PatternsStore.addChangeListener(this.updatePatterns);
    this.updatePatterns();
  },

  componentWillUnmount: function() {
    PatternsStore.removeChangeListener(this.updatePatterns);
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

  createPattern: function(e) {
    e.preventDefault();
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

      // build the list of patterns
      var items = [];
      for(var patternName in this.state.patterns) {
        if(this.state.patterns.hasOwnProperty(patternName)) {
          items.push(
            <NavItem key={patternName} eventKey={patternName}>
              {patternName}
            </NavItem>);
        }
      }

      if(items.length === 0) {
        if(AuthStore.authenticated())
          items = <Well>It looks like you have no patterns defined yet.</Well>;
        else
          items = <Well>You must be signed in to create named patterns.</Well>;
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
                 disabled={!AuthStore.authenticated()}
                 hasFeedback />
        </form>
        </div>;

      var patternEditor = <div/>;
      if(this.state.activePatternName) {
        var activeQuery = this.state.patterns[this.state.activePatternName];
        patternEditor = <PatternEditor patternName={this.state.activePatternName}
                                       config={this.props.config}
                                       initialQuery={activeQuery} />;
      }
      return <Row>
        <Col md={2}>{patternChooser}</Col>
        <Col md={10}>{patternEditor}</Col>
      </Row>;
    }
  }
});

module.exports = PatternsInterface;
