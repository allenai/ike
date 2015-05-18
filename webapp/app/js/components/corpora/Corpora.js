var React = require('react');
var bs = require('react-bootstrap');
var Input = bs.Input;
var Corpora = React.createClass({
  render: function() {
    return (
      <div>
        {this.props.corpora.value.map(function(corpus, i) {
          return (
            <div key={i} className="corpora">
              <Input
                type='checkbox'
                label={corpus.name}
                checked={corpus.selected}
                onChange={this.props.toggleCorpora(i)}>
              </Input>
              <p>{corpus.description}</p>
            </div>
          )
        }.bind(this))}
      </div>
    );
  }
});

module.exports = Corpora;