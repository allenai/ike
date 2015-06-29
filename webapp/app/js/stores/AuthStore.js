'use strict';

const React = require('react');
const AppDispatcher = require('../dispatcher/AppDispatcher');
const EventEmitter = require('events').EventEmitter;
const AuthConstants = require('../constants/AuthConstants');
const assign = require('object-assign');
const CHANGE_EVENT = 'change';

const AuthStore = assign({}, EventEmitter.prototype, {

  /**
   * Save (or delete) the user's info via localStorage
   */
  setAuthState(userEmail, userImageUrl) {
    if (userEmail) {
      localStorage.userEmail = userEmail;
      localStorage.userImageUrl = userImageUrl; 
    } else {
      delete localStorage.userEmail;
      delete localStorage.userImageUrl;
    }
    this.emitChange();
  },

  /**
   * Sign in callback function for google auth
   */
  onSignIn(authResult) {
    if (authResult['status']['signed_in']) {
      gapi.client.load('plus','v1', function() {
        var request = gapi.client.plus.people.get({ userId: 'me' });
        request.execute(function(resp) {
          var userEmail = resp.emails[0].value;
          AuthStore.setAuthState(userEmail, resp.image.url);
        });
      });
    } else {
      AuthStore.setAuthState(null);
    }
  },

  /**
   * Sign in function for google auth
   */
  signIn() {
    var additionalParams = {
      scope: 'email',
      callback: this.onSignIn,
      cookiepolicy: 'single_host_origin',
      clientid: '793503486502-8q1pf7shj3jq7ak2q8ib1ca5hlufdfv7.apps.googleusercontent.com'
    };
    gapi.auth.signIn(additionalParams);
  },

  /**
   * Sign out
   */
  signOut() {
    gapi.auth.signOut();
    this.setAuthState(null);
  },

  /**
   * Return a user's email address
   */
  getUserEmail() {
    return localStorage['userEmail'];
  },

  /**
   * Return a user's iamge url
   */
  getUserImageUrl() {
    return localStorage['userImageUrl'];
  },

  /**
   * Return boolean if they are signed in
   */
  authenticated() {
    return !!localStorage['userEmail'];
  },

  emitChange() {
    this.emit(CHANGE_EVENT);
  },

  addChangeListener: function(callback) {
    this.on(CHANGE_EVENT, callback);
  },

  removeChangeListener: function(callback) {
    this.removeListener(CHANGE_EVENT, callback);
  }
});

AuthStore.Mixin = {
  statics: {
    willTransitionTo: function (transition) {
      if (!AuthStore.authenticated()) {
        transition.redirect('/search');
      }
    }
  }
}

// Register callback to handle all updates
AppDispatcher.register(function(action) {

  if (action.actionType === AuthConstants.SIGN_IN) {
    AuthStore.signIn();
  } else {
    AuthStore.signOut();
  }

});

module.exports = AuthStore;

