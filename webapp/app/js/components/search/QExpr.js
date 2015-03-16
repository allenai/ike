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
    } else if (attr == 'qexprs') {
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
    childPath.push(attr);
    if (attr == 'qexprs') {
      childPath.push(index);
    }
    return (
      <Node key={index}>
        <QExpr
          qexpr={childExpr}
          path={childPath}
          config={config}
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
var QSeq = React.createClass({
  mixins: [InnerNodeMixin],
  name: 'Sequence'
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
    var requestData = {
      body: JSON.stringify(query),
      uri: '/api/wordInfo',
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
  replaceWithClusterFromWord: function(wordInfo) {
    var replacement = {
      type: 'QClusterFromWord',
      value: wordInfo.clusterId.length + 1,
      wordValue: wordInfo.word,
      clusterId: wordInfo.clusterId
    };
    this.updateSelf(replacement);
  },
  replaceWithPosFromWord: function(wordInfo) {
    var replacement = {
      type: 'QPosFromWord',
      wordValue: wordInfo.word,
      posTags: wordInfo.posTags
    };
    this.updateSelf(replacement);
  },
  toClusterFromWord: function() {
    var replace = this.replaceWithClusterFromWord;
    this.getWordInfo(replace);
  },
  toPosFromWord: function() {
    var replace = this.replaceWithPosFromWord;
    this.getWordInfo(replace);
  },
  render: function() {
    var value = this.props.qexpr.value;
    var button = (
      <div>
      <DropdownButton bsStyle="link" title={value}>
        <MenuItem eventKey={1} onClick={this.toClusterFromWord}>Generalize to similar words...</MenuItem>
        <MenuItem eventKey={1} onClick={this.toPosFromWord}>Generalize by POS tag...</MenuItem>
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
var QCluster = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Cluster<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
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
var QClusterFromWord = React.createClass({
  mixins: [QExprMixin],
  handleChange: function(e) {
    var qexpr = this.props.qexpr;
    var value = this.refs.slider.getDOMNode().value;
    qexpr.value = parseInt(value);
    this.updateSelf(qexpr);
  },
  convertToWord: function() {
    var word = this.props.qexpr.wordValue;
    var replacement = {
      value: word,
      type: "QWord"
    };
    this.updateSelf(replacement);
  },
  render: function() {
    var qexpr = this.props.qexpr;
    var value = qexpr.value;
    var wordValue = qexpr.wordValue;
    var clusterId = qexpr.clusterId;
    var button = (
      <Button
        onClick={this.convertToWord}
        bsSize="xsmall"
        className="pull-right"
        bsStyle="danger">
        <Glyphicon glyph="remove"/>
      </Button>
    );
    var slider =
      <input
        ref="slider"
        type="range"
        className="vslider"
        min={1}
        max={clusterId.length + 1}
        step={1}
        value={value}
        onChange={this.handleChange}/>;
    var header = 'Words similar to "' + wordValue + '"'
    var headerValue = <div style={{fontSize: 'small'}}>{header}</div>;
    return (
      <Panel header={headerValue}>
        <div>
          More Similar
          {slider}
          Less Similar
        </div>
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
    QPos: QPos,
    QSeq: QSeq,
    QDict: QDict,
    QCluster: QCluster,
    QWildcard: QWildcard,
    QNamed: QNamed,
    QUnnamed: QUnnamed,
    QNonCap: QNonCap,
    QStar: QStar,
    QPlus: QPlus,
    QDisj: QDisj,
    QClusterFromWord: QClusterFromWord,
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
      var path = this.props.path;
      var implProps = {
        qexpr: qexpr,
        path: path,
        rootState: rootState,
        handleChange: handleChange,
        config: config
      };
      return React.createElement(component, implProps);
    } else {
      console.error('Could not find expression type: ' + type);
      return <div/>;
    }
  }
});
module.exports = QExpr;
