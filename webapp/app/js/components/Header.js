"use strict";

const React = require('react/addons');
const bs = require('react-bootstrap');
const AppDispatcher = require('../dispatcher/AppDispatcher');
const AuthStore = require('../stores/AuthStore.js');
const AuthConstants = require('../constants/AuthConstants');

const Header = React.createClass({

  propTypes: {
    authenticated: React.PropTypes.bool.isRequired
  },

  signIn(e) {
    e.preventDefault();
    AppDispatcher.dispatch({
      actionType: AuthConstants.SIGN_IN
    });
  },

  signOut(e) {
    e.preventDefault();
    AppDispatcher.dispatch({
      actionType: AuthConstants.SIGN_OUT
    });
  },

  renderAuth() {
    if (this.props.authenticated) {
      var userEmail = AuthStore.getUserEmail();
      var userImageUrl = AuthStore.getUserImageUrl();
      return (
        <div className="auth pull-right">
          <img src={userImageUrl ? userImageUrl : "/assets/blank_user.png"}/>
          <span>{userEmail}</span>
          <a href="#" className="btn" onClick={this.signOut}>Sign Out</a>
        </div>
      )
    } else {
      return (
        <div className="auth pull-right">
          <a href="#" className="btn btn-default" onClick={this.signIn}>Sign In</a>
        </div>
      )
    }
  },

  render() {
    return (<header>
      <a href="/"><img src="/assets/logo.png" width="64"/></a>
      {this.renderAuth()}
    </header>);
  }
});

module.exports = Header;
