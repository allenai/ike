var React = require('react');
var bs = require('react-bootstrap');
var Modal = bs.Modal;
var ModalTrigger = bs.ModalTrigger;
var Glyphicon = bs.Glyphicon;
var Corpora = require('../corpora/Corpora.js');

var CorpusSelector = React.createClass({
  propTypes: {
    corpora: React.PropTypes.object.isRequired,
    toggleCorpora: React.PropTypes.func.isRequired
  },

  selectedCorpora: function() {
    return this.props.corpora.value.filter(function(corpus) {
      return corpus.selected;
    });
  },

  renderCorporaLabel: function() {
    // Get the number of selected corpora
    var corpora = this.props.corpora.value;
    var selectedCorpora = this.selectedCorpora();
    var corporaLabel = 'Searching ';
    if (selectedCorpora.length === corpora.length) {
      corporaLabel += ' All ';
    }
    corporaLabel += (selectedCorpora.length === 1)
      ? selectedCorpora[0].name + ' Corpus'
      : selectedCorpora.length + ' Corpora';

    return <span>{corporaLabel}</span>;
  },

  render: function() {
    // Bootstrap's Modal requires a function, so we give it one.
    var onRequestHide = function() {};

    var overlay =
      <Modal onRequestHide={onRequestHide}
             bsStyle='primary'
             title='Select Corpora to Query'
             animation={false}>
        <div className='modal-body'>
          <Corpora corpora={this.props.corpora} toggleCorpora={this.props.toggleCorpora} />
        </div>
      </Modal>;
    return <ModalTrigger trigger='click' modal={overlay}>
      <a className="corpora-modal-trigger"><Glyphicon glyph="cog"/> {this.renderCorporaLabel()}</a>
    </ModalTrigger>;
  }
});

module.exports = CorpusSelector;
