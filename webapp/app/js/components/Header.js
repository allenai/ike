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
      <a href="/"><img style={{ margin: "20px 20px 20px 0" }} src="/assets/logo.png" height="48"/></a>
      {this.renderAuth()}
      <div><a href="https://github.com/allenai/ike/blob/master/USAGE-GUIDE.md">IKE Getting Started Guide</a> | <a href="https://github.com/allenai/ike">IKE Code Repository</a></div>
      <div><i>Learn more: <a href="http://ai2-website.s3.amazonaws.com/publications/IKE_camera_ready_v3.pdf">IKE - An Interactive Tool for Knowledge Extraction</a></i></div>
    </header>);
  }
});

module.exports = Header;
