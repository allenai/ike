var React = require('react/addons');
var bs = require('react-bootstrap');
var PageHeader = bs.PageHeader;
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var DropdownButton = bs.DropdownButton;
var MenuItem = bs.MenuItem;
var SearchInterface = require('./components/search/SearchInterface.js');
var TablesInterface = require('./components/table/TablesInterface.js');
var TableManager = require('./managers/TableManager.js');
var ConfigInterface = require('./components/config/ConfigInterface.js');
var HelpInterface = require('./components/help/HelpInterface.js');
var DictApp = React.createClass({
  mixins: [React.addons.LinkedStateMixin],
  componentDidMount: function() {
    var target = this.linkState('target');

    TableManager.addChangeListener(function(tables) {
      this.setState({tables: tables});
      if(target.value == null && tables) {
        for(var tableName in tables) {
          if(tables.hasOwnProperty(tableName)) {
            target.requestChange(tableName);
            break;
          }
        }
      }

    }.bind(this));
  },
  getInitialState: function() {
    return {
      config: {
        limit: 1000,
        evidenceLimit: 10,
        hideAdded: false,
        groupsPerPage: 25,
        ml: {
           disable: true,
           depth: 2,
           beamSize: 30,
           maxSampleSize: 15000,
           pWeight: 1.0,
           nWeight: -5.0,
           uWeight: -1.0,
           allowDisjunctions: false
        }
      },
      results: {
        groups: [],
        qexpr: null,
        pending: false,
        request: null,
        errorMessage: null
      },
      userEmail: null,
      userImageUrl: null,
      tables: [],
      target: null
    };
  },
  renderContent: function() {
    var target = this.linkState('target');
    var results = this.linkState('results');
    var config = this.linkState('config');
    var searchInterface =
      <SearchInterface config={config} results={results} target={target}/>;
    var tablesInterface = <TablesInterface target={target}/>;
    var configInterface = <ConfigInterface config={config}/>;
    var helpInterface = <HelpInterface/>;
    return (
      <div>
        <TabbedArea animation={false}>
          <TabPane className="mainContent" eventKey={1} tab="Search">
            {searchInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={2} tab="Tables">
            {tablesInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={3} tab="Configuration">
            {configInterface}
          </TabPane>
          <TabPane className="mainContent" eventKey={4} tab="Help">
            {helpInterface}
          </TabPane>
        </TabbedArea>
      </div>
    );
  },
  onSignIn: function(googleUser) {
    var profile = googleUser.getBasicProfile();
    this.setState({
      userEmail: profile.getEmail(),
      userImageUrl: profile.getImageUrl()
    });
    TableManager.setUserEmail(profile.getEmail());
  },
  signOut: function() {
    var self = this;
    gapi.auth2.getAuthInstance().signOut().then(function() {
      self.setState({
        userEmail: null,
        userImageUrl: null
      });
    });
  },
  renderHeader: function() {
    var signInButton =
      <div
        className="g-signin2"
        data-onsuccess="onSignIn"/>
    window.onSignIn = this.onSignIn;
    var userImage =
      <img
        src={this.state.userImageUrl ? this.state.userImageUrl : "/assets/blank_user.png"}
        width="32"
        height="32"
        border="1"/>
    var authMenuOptions = [<MenuItem key="signIn">{signInButton}</MenuItem>];
    if(this.state.userEmail)
      authMenuOptions.push(<MenuItem key="signOut" onSelect={this.signOut}>Sign out</MenuItem>);

    var authButtons =
      <DropdownButton title={userImage} pullRight>
        {authMenuOptions}
      </DropdownButton>;

    return (<div>
      <a href="/"><img src="/assets/logo.png" width="64"/></a>
      <em>&ldquo;The Pacific Northwest&#39;s Cutest Extraction Tool&rdquo;</em>
      <div className="pull-right">{authButtons}</div>
    </div>);
  },
  render: function() {
    var content = this.renderContent();
    var header = this.renderHeader();
    return <div>{header}{content}</div>;
  }
});
React.render(<DictApp/>, document.body);
