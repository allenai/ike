var React = require('react');
var xhr = require('xhr');
var SearchInterface = require('./SearchInterface.js');
var BlackLabResults = require('./BlackLabResults.js');
var DictionaryInterface = require('./DictionaryInterface.js');
var bs = require('react-bootstrap');
var TabbedArea = bs.TabbedArea;
var TabPane = bs.TabPane;

var CorpusSearcher = React.createClass({

  createDictionary: function(name) {
    alert('create dictionary: ' + name);
  },

  deleteDictionary: function(name) {
    alert('delete dictionary: ' + name);
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

  deleteEntry: function(name, type, i) {
    var dicts = this.state.dictionaries;
    var dict = dicts[name];
    var entries = dict[type];
    entries.splice(i, i+1);
    this.setState({dictionaries: dicts});
  },

  getInitialState: function() {
    return {
      results: [], 
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
    xhr({
      body: JSON.stringify(queryObj),
      uri: '/api/search',
      headers: {
        'Content-Type': 'application/json'
      },
      method: 'POST'
    }, function(err, resp, body) {
      this.setState({results: JSON.parse(body)});
    }.bind(this));
  },
  render: function() {
    var dictionaryCallbacks = {
      addEntry: this.addEntry
    };
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

          <BlackLabResults results={this.state.results}/>

        </div>
      </section>
    );
  }
});
module.exports = CorpusSearcher;
