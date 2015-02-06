var React = require('react');
var bs = require('react-bootstrap');
var xhr = require('xhr');
var Glyphicon = bs.Glyphicon;
var DropdownButton = bs.DropdownButton;
var tree = require('./Tree.js');
var MenuItem = bs.MenuItem;
var Node = tree.Node;
var Tree = tree.Tree;

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
    return <div>POS<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
  }
});
var QWord = React.createClass({
  mixins: [QExprMixin],
  getWordInfo: function() {
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
    var request = xhr(requestData, this.wordInfoCallback);
  },
  wordInfoCallback: function(err, resp, body) {
    if (resp.statusCode == 200) {
      var wordInfo = JSON.parse(body);
      this.replaceWithClusterFromWord(wordInfo);
    } else {
      alert('Could not get word info');
    }
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
  render: function() {
    var value = this.props.qexpr.value;
    var button = (
      <div>
      <DropdownButton bsStyle="link" title={value}>
        <MenuItem eventKey={1} onClick={this.getWordInfo}>Generalize to Similar Words...</MenuItem>
      </DropdownButton>
      </div>
    );
    return button;
  }
});
var QDict = React.createClass({
  mixins: [QExprMixin],
  render: function() {
    return <div>Dict<Tree><Node>{this.props.qexpr.value}</Node></Tree></div>;
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
var QClusterFromWord = React.createClass({
  mixins: [QExprMixin],
  handleChange: function(e) {
    var qexpr = this.props.qexpr;
    var value = this.refs.slider.getDOMNode().value;
    qexpr.value = parseInt(value);
    this.updateSelf(qexpr);
  },
  render: function() {
    var qexpr = this.props.qexpr;
    var value = qexpr.value;
    var wordValue = qexpr.wordValue;
    var clusterId = qexpr.clusterId;
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
    return (
      <div>
        {wordValue}
        <div>
          More Similar
          {slider}
          Less Similar
        </div>
      </div>
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
    QClusterFromWord: QClusterFromWord
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
