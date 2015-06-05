var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Glyphicon = bs.Glyphicon;
var Button = bs.Button;
var DropdownButton = bs.DropdownButton;
var tree = require('./Tree.js');
var MenuItem = bs.MenuItem;
var Node = tree.Node;
var Tree = tree.Tree;
var Label = bs.Label;
var Panel = bs.Panel;
var Input = bs.Input;

var QExprMixin = {
  propTypes: {
    rootState: React.PropTypes.shape({
      value: React.PropTypes.object.isRequired,
      requestChange: React.PropTypes.func.isRequired
    }).isRequired,
    qexpr: React.PropTypes.object.isRequired,
    handleChange: React.PropTypes.func.isRequired,
    path: React.PropTypes.arrayOf(React.PropTypes.oneOfType([
      React.PropTypes.string,
      React.PropTypes.number
    ])).isRequired
  },
  replaceExprAtPath: function(replacement, callback) {
    var rootState = this.props.rootState;
    var path = this.props.path.slice();
    var pointer = rootState.value;
    if (path.length == 0) {
      rootState.requestChange(replacement, callback);
    } else {
      while (path.length > 1) {
        pointer = pointer[path[0]];
        path = path.slice(1);
      }
      pointer[path[0]] = replacement;
      rootState.requestChange(rootState.value, callback);
    }
  },
  updateSelf: function(replacement) {
    var rootState = this.props.rootState;
    var callback = this.props.handleChange;
    this.replaceExprAtPath(replacement, callback);
  },
  children: function() {
    var attr = this.pathAttr();
    if (attr == 'qexpr') {
      return [this.props.qexpr[attr]];
    } else if (attr == 'qwords' || attr == 'qexprs') {
      return this.props.qexpr[attr];
    } else {
      return [];
    }
  },
  pathAttr: function() {
    var qexpr = this.props.qexpr;
    if ('qexpr' in qexpr) {
      return 'qexpr';
    } else if ('qexprs' in qexpr) {
      return 'qexprs';
    } else if ('qwords' in qexpr) {
      return 'qwords';
    } else {
      return null;
    }
  },
  makeChildComponent: function(childExpr, index) {
    var attr = this.pathAttr();
    var childPath = this.props.path.slice();
    var handleChange = this.props.handleChange;
    var rootState = this.props.rootState;
    var config = this.props.config;
    var makeUri = this.props.makeUri;
    childPath.push(attr);
    if (attr == 'qexprs' || attr == "qwords") {
      childPath.push(index);
    }
    return (
      <Node key={index}>
        <QExpr
          qexpr={childExpr}
          path={childPath}
          config={config}
          makeUri={makeUri}
          handleChange={handleChange}
          rootState={rootState}/>
      </Node>
    );
  },
  childComponents: function() {
    var children = this.children();
    return children.map(this.makeChildComponent);
  }
}
var InnerNodeMixin = {
  mixins: [QExprMixin],
  render: function() {
    var name;
    if (typeof(this.name) == 'function') {
      name = this.name();
    } else {
      name = this.name;
    }
    return <div>{name}<Tree>{this.childComponents()}</Tree></div>;
  }
};
var similarPhrases = function(phrase, callback) {
  var url = '/api/similarPhrases';
  var requestData = {
    uri: url + "?phrase=" + encodeURIComponent(phrase),
    method: 'GET'
  };
  var xhrCallback = function(err, resp, body) {
    if (resp.statusCode == 200) {
      var phrases = JSON.parse(body);
      callback(phrases.phrases);
    } else {
      alert('Something went wrong while finding similar phrases.');
    }
  };
  var request = xhr(requestData, xhrCallback);
};

var QSeq = React.createClass({
  mixins: [QExprMixin],
  phrase: function() {
    var children = this.children();
    var words = children.map(function(child) { return child.value });
    var phrase = words.join(" ");
    return phrase;
  },
  replaceWithSimilarPhrases: function(phraseData) {
    var children = this.children();
    var replacement = {
      type: 'QSimilarPhrases',
      qwords: children,
      pos: 0,
      phrases: phraseData
    };
    this.updateSelf(replacement);
  },
  similarPhrasesClick: function() {
    similarPhrases(this.phrase(), this.replaceWithSimilarPhrases);
  },
  childTypes: function() {
    return this.children().map(function(child) {
      return child.type;
    });
  },
  hasQWordChildren: function() {
    return this.childTypes().every(function(type) { return type == 'QWord' });
  },
  menuButton: function() {
    return (
      <div>
      <DropdownButton bsStyle="link" title="Sequence">
        <MenuItem eventKey={1} onClick={this.similarPhrasesClick}>
          Phrases similar to "{this.phrase()}"...
        </MenuItem>
      </DropdownButton>
      </div>
    );
  },
  renderLabel: function() {
    if (this.hasQWordChildren()) {
      return this.menuButton();
    } else {
      return <span>Sequence</span>;
    }
  },
  render: function() {
    var label = this.renderLabel();
    return <div>{label}<Tree>{this.childComponents()}</Tree></div>;
  }
});
var QDisj = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Or'
});
var QPos = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var value = this.props.qexpr.value;
    var menu = <DropdownButton bsStyle="link" title={value}></DropdownButton>;
    return menu;
  }
});
var QWord = React.createClass({
  mixins: [QExprMixin],
  getWordInfo: function(callback) {
    var word = this.props.qexpr.value;
    var config = this.props.config.value;
    var query = {
      word: word,
      config: config
    };
    var uri = this.props.makeUri('wordInfo');
    var requestData = {
      body: JSON.stringify(query),
      uri: uri,
      method: 'POST',
      headers: {'Content-Type': 'application/json'}
    };
    var xhrCallback = function(err, resp, body) {
      if (resp.statusCode == 200) {
        var wordInfo = JSON.parse(body);
        callback(wordInfo);
      } else {
        alert('Could not get word info');
      }
    };
    var request = xhr(requestData, xhrCallback);
  },
  replaceWithPosFromWord: function(wordInfo) {
    var replacement = {
      type: 'QPosFromWord',
      wordValue: wordInfo.word,
      posTags: wordInfo.posTags
    };
    this.updateSelf(replacement);
  },
  toPosFromWord: function() {
    var replace = this.replaceWithPosFromWord;
    this.getWordInfo(replace);
  },
  replaceWithSimilarPhrases: function(phraseData) {
    var children = [this.props.qexpr]
    var replacement = {
      type: 'QSimilarPhrases',
      qwords: children,
      pos: 0,
      phrases: phraseData
    };
    this.updateSelf(replacement);
  },
  toSimilarPhrases: function() {
    var replace = this.replaceWithSimilarPhrases;
    var phrase = this.props.qexpr.value;
    similarPhrases(phrase, replace);
  },
  render: function() {
    var value = this.props.qexpr.value;
    var button = (
      <div>
      <DropdownButton bsStyle="link" title={value}>
        <MenuItem eventKey={1} onClick={this.toPosFromWord}>Generalize by POS tag...</MenuItem>
        <MenuItem eventKey={1} onClick={this.toSimilarPhrases}>Generalize to similar phrases...</MenuItem>
      </DropdownButton>
      </div>
    );
    return button;
  }
});
var QDict = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    var value = this.props.qexpr.value;
    var dollarized = '$' + value;
    var menu = <DropdownButton bsStyle="link" title={dollarized}></DropdownButton>;
    return menu;
  }
});
var QWildcard = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Wildcard</div>;
  }
});
var QNamed = React.createClass({
  mixins: [InnerNodeMixin],
  name: function() {
    return 'Capture(' + this.props.qexpr.name + ')';
  }
});
var QUnnamed = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Capture'
});
var QGeneralizePhrase = React.createClass({
  mixins: [QExprMixin],
  phrase: function() {
    var children = this.children();
    var words = children.map(function(child) { return child.value });
    var phrase = words.join(" ");
    if (children.length > 1) {
      phrase = "\"" + phrase + "\"";
    }
    return phrase;
  },
  replaceWithSimilarPhrases: function(phraseData) {
    var children = this.children();
    var replacement = {
      type: 'QSimilarPhrases',
      qwords: children,
      pos: this.props.qexpr.pos,
      phrases: phraseData
    };
    this.updateSelf(replacement);
  },
  similarPhrasesClick: function() {
    similarPhrases(this.phrase(), this.replaceWithSimilarPhrases);
  },
  menuButton: function() {
    return (
      <div>
      <DropdownButton bsStyle="link" title={this.phrase() + "~" + this.props.qexpr.pos}>
        <MenuItem eventKey={1} onClick={this.similarPhrasesClick}>
          Adjust Similarity
        </MenuItem>
      </DropdownButton>
      </div>
    );
  },
  render: function() {
    return (
      <div>
        {this.menuButton()}
      </div>)
    ;
  }
});
var QSimilarPhrases = React.createClass({
  mixins: [QExprMixin],
  numPhrases: function() {
    return this.props.qexpr.phrases.length;
  },
  pos: function() {
    return this.props.qexpr.pos;
  },
  phrase: function() {
    var qwords = this.props.qexpr.qwords;
    var words = qwords.map(function(qw) { return qw.value; });
    var phrase = words.join(" ");
    if (words.length > 1) {
      phrase = "\"" + phrase + "\"";
    }
    return phrase;
  },
  handleChange: function(e) {
    var value = e.target.value;
    var qexpr = this.props.qexpr;
    var max = this.numPhrases();
    qexpr.pos = max - Number(value);
    this.updateSelf(qexpr);
  },
  renderSlider: function() {
    var max = this.numPhrases();
    var value = Math.max(0,max - this.pos());
    var slider =
      <input
        ref="slider"
        type="range"
        className="vslider"
        min={0}
        max={max}
        step={1}
        value={value}
        onChange={this.handleChange}/>;
    return slider;
  },
  render: function() {
  return (
      <Panel header={this.phrase() + "~" + this.pos()}>
        More Similar
        {this.renderSlider()}
        Less Similar
      </Panel>
    );
  }
});
var QNonCap = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'NonCapture'
});
var QStar = React.createClass({
  mixins: [InnerNodeMixin],
  name: <Glyphicon glyph="asterisk"/>
});
var QPlus = React.createClass({
  mixins: [InnerNodeMixin],
  name: <Glyphicon glyph="plus"/>
});
var QRepetition = React.createClass({
  mixins: [InnerNodeMixin],
  name: function() {
    return "Repeat [" + this.props.qexpr.min + "," + this.props.qexpr.max + "]"
  }
});
var QPosFromWord = React.createClass({
  mixins: [QExprMixin],
  handleChange: function(e) {
    var value = e.target.value;
    var qexpr = this.props.qexpr;
    qexpr.value = value;
    this.updateSelf(qexpr);
  },
  makeOption: function(tag, i) {
    return <option key={i} value={tag}>{tag}</option>;
  },
  render: function() {
    var qexpr = this.props.qexpr;
    var word = qexpr.wordValue;
    var tags = Object.keys(qexpr.posTags);
    tags.sort(function(t1, t2) {
      return qexpr.posTags[t2] - qexpr.posTags[t1];
    });
    var header = <div style={{fontSize: 'small'}}>{'POS Tags for "' + word + '"'}</div>;
    var placeholder;
    if (!('value' in qexpr)) {
      placeholder = <option>Possible POS Tags</option>;
    }
    return (
      <Panel header={header}>
        <Input type="select" onChange={this.handleChange} value={this.props.qexpr.value}>
          {placeholder}
          {tags.map(this.makeOption)}
        </Input>
      </Panel>
    );
  }
});
var QExpr = React.createClass({
  mixins: [QExprMixin],
  getDefaultProps: function() {
    return {
      path: []
    }
  },
  qexprComponents: {
    QWord: QWord,
    QSimilarPhrases: QSimilarPhrases,
    QGeneralizePhrase: QGeneralizePhrase,
    QPos: QPos,
    QSeq: QSeq,
    QDict: QDict,
    QWildcard: QWildcard,
    QNamed: QNamed,
    QUnnamed: QUnnamed,
    QNonCap: QNonCap,
    QStar: QStar,
    QPlus: QPlus,
    QRepetition: QRepetition,
    QDisj: QDisj,
    QPosFromWord: QPosFromWord
  },
  render: function() {
    var qexpr = this.props.qexpr;
    var type = qexpr.type;
    if (type in this.qexprComponents) {
      var component = this.qexprComponents[type];
      var rootState = this.props.rootState;
      var handleChange = this.props.handleChange;
      var config = this.props.config;
      var makeUri = this.props.makeUri;
      var path = this.props.path;
      var implProps = {
        qexpr: qexpr,
        path: path,
        rootState: rootState,
        handleChange: handleChange,
        config: config,
        makeUri: makeUri
      };
      return React.createElement(component, implProps);
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
