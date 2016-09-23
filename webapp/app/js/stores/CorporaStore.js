'use strict';

const React = require('react');
const AppDispatcher = require('../dispatcher/AppDispatcher');
const EventEmitter = require('events').EventEmitter;
const CorporaConstants = require('../constants/CorporaConstants');
const xhr = require('xhr');
const assign = require('object-assign');
const CHANGE_EVENT = 'change';

const CorporaStore = assign({}, EventEmitter.prototype, {
  getCorpora() {
    if(localStorage.corpora)
      return JSON.parse(localStorage.corpora);
    else
      return [];
  },

  getCorpusNames() {
    function isSelected(corpus) {
      return corpus.defaultSelected
    }
    return this.getCorpora().filter(isSelected).map(function(corpus) {
      return corpus.name;
    });
  },

  refresh() {
    var self = this;

    xhr({
      uri: '/api/corpora',
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        var newCorpora = JSON.parse(body).map(function (corpus, i) {
          return {
            name: corpus.name,
            description: corpus.description,
            defaultSelected: corpus.defaultSelected
          }
        });

        if(JSON.stringify(newCorpora) !== JSON.stringify(self.getCorpora())) {
          localStorage.corpora = JSON.stringify(newCorpora);
          self.emit(CHANGE_EVENT);
        }
      } else {
        console.warn("Updating corpora failed: " + body);
      }
    });
  },

  addChangeListener(callback) {
    this.on(CHANGE_EVENT, callback);
  },

  removeChangeListener(callback) {
    this.removeListener(CHANGE_EVENT, callback);
  }
});

AppDispatcher.register(function(action) {
  if (action.actionType === CorporaConstants.REFRESH) {
    CorporaStore.refresh();
  }
});

CorporaStore.refresh();

module.exports = CorporaStore;
