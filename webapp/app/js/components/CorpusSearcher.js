var React = require('react');
var xhr = require('xhr');
var SearchInterface = require('./SearchInterface.js');
var GroupedBlackLabResults = require('./GroupedBlackLabResults.js');
var DictionaryInterface = require('./DictionaryInterface.js');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;
var Alert = bs.Alert;

var CorpusSearcher = React.createClass({

  setTargetDictionary: function(name) {
    console.log('updating to ' + name);
    this.setState({targetDictionary: name});
  },

  createDictionary: function(name) {
    var dicts = this.state.dictionaries;
    if (!(name in dicts)) {
      dicts[name] = {name: name, positive: [], negative: []};
      this.setState({dictionaries: dicts, targetDictionary: name});
    }
  },

  deleteDictionary: function(name) {
    var dicts = this.state.dictionaries;
    if (name in dicts) {
      delete dicts[name];
      var newState = {dictionaries: dicts};
      if (this.state.targetDictionary == name) {
        newState['targetDictionary'] = null;
      }
      this.setState(newState);
    }
  },

  addEntry: function(name, type, entry) {
    var dicts = this.state.dictionaries;
    var dict = dicts[name];
    var entries = dict[type];
    if (entries.indexOf(entry) < 0) {
      entries.push(entry);
    }
    this.setState({dictionaries: dicts});
  },

  deleteEntry: function(name, type, entry) {
    var dicts = this.state.dictionaries;
    var dict = dicts[name];
    var entries = dict[type];
    var index = entries.indexOf(entry);
    if (index >= 0) {
      entries.splice(index, 1);
    }
    this.setState({dictionaries: dicts});
  },

  getInitialState: function() {
    return {
      results: [], 
      groupedResults: [],
      error: false,
      errorMessage: null,
      hasContent: false,
      loading: false,
      targetDictionary: null,
      dictionaries: {}
    };
  },

  executeSearch: function(queryObj) {
    this.setState({loading: true});
    queryObj['dictionaries'] = this.state.dictionaries;
    xhr({
      body: JSON.stringify(queryObj),
      uri: '/api/groupedSearch',
      headers: {
        'Content-Type': 'application/json'
      },
      method: 'POST'
    }, function(err, resp, body) {
      var updatedState;
      if (resp.statusCode == 200) {
        updatedState = {
          groupedResults: JSON.parse(body),
          error: false,
          hasContent: true,
          loading: false
        };
      } else {
        updatedState = {
          error: true,
          hasContent: false,
          errorMessage: resp.body,
          loading: false
        };
      }
      this.setState(updatedState);
    }.bind(this));
  },
  render: function() {
    var dictionaryCallbacks = {
      addEntry: this.addEntry,
      deleteEntry: this.deleteEntry,
      createDictionary: this.createDictionary,
      deleteDictionary: this.deleteDictionary
    };
    var targetDictionary = this.state.targetDictionary;
    var searchCallbacks = {
      executeSearch: this.executeSearch,
      setTargetDictionary: this.setTargetDictionary,
      addEntry: function(name) {
        this.addEntry(targetDictionary, 'positive', name);
      }.bind(this)
    };
    var content;
    if (this.state.error) {
      content = <Alert bsStyle="danger">{this.state.errorMessage}</Alert>;
    } else if (this.state.loading) {
      content = "Loading...";
    } else if (this.state.hasContent) {
      content = <GroupedBlackLabResults results={this.state.groupedResults} callbacks={searchCallbacks} targetDictionary={targetDictionary} />;
    } else {
      content = null;
    }
    return (
      <section>
        <div className="col-md-4">
          <TabbedArea defaultActiveKey={1} animation={false}>
            <TabPane tab="Search" eventKey={1}>
              <SearchInterface callbacks={searchCallbacks} targetDictionary={this.state.targetDictionary} dictionaries={this.state.dictionaries}/>
            </TabPane>
            <TabPane tab="Dictionaries" eventKey={2}>
              <DictionaryInterface dictionaries={this.state.dictionaries} callbacks={dictionaryCallbacks} targetDictionary={targetDictionary}/> 
            </TabPane>
          </TabbedArea>
        </div>
        <div className="col-md-8">
          {content}
        </div>
      </section>
    );
  }
});
module.exports = CorpusSearcher;
