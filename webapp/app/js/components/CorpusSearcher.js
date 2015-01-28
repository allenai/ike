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

  createDictionary: function(name) {
    var dicts = this.state.dictionaries;
    if (!(name in dicts)) {
      dicts[name] = {name: name, positive: [], negative: []};
      this.setState({dictionaries: dicts});
    }
  },

  deleteDictionary: function(name) {
    var dicts = this.state.dictionaries;
    if (name in dicts) {
      delete dicts[name];
      this.setState({dictionaries: dicts});
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
      dictionaries: {
        technique: {
          name: 'technique',
          positive: [],
          negative: []
        },
        task: {
          name: 'task',
          positive: ['pos tagging', 'semantic parsing', 'machine translation'],
          negative: ['mert', 'joe smith', 'conll shared task']
        }
      }
    };
  },

  executeSearch: function(queryObj) {
    this.setState({loading: true});
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
    var content;
    if (this.state.error) {
      content = <Alert bsStyle="danger">{this.state.errorMessage}</Alert>;
    } else if (this.state.loading) {
      content = "Loading...";
    } else if (this.state.hasContent) {
      content = <GroupedBlackLabResults results={this.state.groupedResults}/>;
    } else {
      content = null;
    }
    return (
      <section>
        <div className="col-md-4">
          <TabbedArea defaultActiveKey={1} animation={false}>
            <TabPane tab="Search" eventKey={1}>
              <SearchInterface callback={this.executeSearch}/>
            </TabPane>
            <TabPane tab="Dictionaries" eventKey={2}>
              <DictionaryInterface dictionaries={this.state.dictionaries} callbacks={dictionaryCallbacks} /> 
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
