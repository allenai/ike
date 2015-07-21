var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;

var Corpora = React.createClass({
  propTypes: {
    corpora: React.PropTypes.array.isRequired,
    selectedCorpusNames: React.PropTypes.array.isRequired,
    toggleCorpora: React.PropTypes.func.isRequired
  },
  render: function() {
    var self = this;
    return (
      <div>
        {this.props.corpora.map(function(corpus, i) {
          return (
            <div key={i} className="corpora">
              <Input
                type='checkbox'
                label={corpus.name}
                checked={self.props.selectedCorpusNames.indexOf(corpus.name) >= 0}
                onChange={self.props.toggleCorpora.bind(undefined, i)}>
              </Input>
              <p>{corpus.description}</p>
            </div>);
        })}
      </div>
    );
  }
});

module.exports = Corpora;
