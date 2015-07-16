'use strict';

const React = require('react');
const AppDispatcher = require('../dispatcher/AppDispatcher');
const EventEmitter = require('events').EventEmitter;
const assign = require('object-assign');
const AuthStore = require('./AuthStore');
var xhr = require('xhr');

const CHANGE_EVENT = 'change';

var userEmail = null;
var patterns = [];
var error = null;

const NamedPatternsStore = assign({}, EventEmitter.prototype, {
  setUserEmail(newUserEmail) {
    if(newUserEmail !== userEmail) {
      userEmail = newUserEmail;
      this.refreshPatternsFromServer();
    }
  },

  refreshPatternsFromServer() {
    patterns = [];
    error = null;

    var self = this;

    // Get the patterns from the server
    if(userEmail) {
      xhr({
        uri: '/api/patterns/' + encodeURIComponent(userEmail),
        method: 'GET'
      }, function (err, resp, body) {
        if (resp.statusCode == 200) {
          // set patterns
          var patternsObjects = JSON.parse(body);
          var newPatterns = {};
          patternsObjects.forEach(function (patternObject) {
            newPatterns[patternObject.name] = patternObject.pattern;
          });

          patterns = newPatterns;
          error = null;
        } else {
          error = resp.body + " (" + resp.statusCode + ")";
        }
        self.emitChange();
      });
    }

    this.emitChange();
  },

  savePattern(name, pattern) {
    if(userEmail) {
      patterns[name] = pattern;
      xhr({
        uri: '/api/patterns/' + encodeURIComponent(userEmail) + '/' + name,
        method: 'PUT',
        body: pattern
      }, function(err, response, body) {
        if(response.statusCode !== 200) {
          console.log("Unexpected response writing a table: " + JSON.stringify(response));
        }
      });
      this.emitChange();
    }
  },

  getPatterns() {
    return patterns;
  },

  getError() {
    return error;
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

// initialize NamedPatternStore
NamedPatternsStore.setUserEmail(AuthStore.getUserEmail());
AuthStore.addChangeListener(function() {
  NamedPatternsStore.setUserEmail(AuthStore.getUserEmail());
});

module.exports = NamedPatternsStore;
