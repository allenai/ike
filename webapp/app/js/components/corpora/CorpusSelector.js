var React = require('react');
var bs = require('react-bootstrap');
var Modal = bs.Modal;
var ModalTrigger = bs.ModalTrigger;
var Glyphicon = bs.Glyphicon;
var Corpora = require('../corpora/Corpora.js');

var CorpusSelector = React.createClass({
  propTypes: {
    corpora: React.PropTypes.array.isRequired,
    selectedCorpusNames: React.PropTypes.array.isRequired,
    toggleCorpora: React.PropTypes.func.isRequired
  },

  renderCorporaLabel: function() {
    // Get the number of selected corpora
    var corpora = this.props.corpora;
    var selectedCorpusNames = this.props.selectedCorpusNames;
    var corporaLabel = 'Searching ';
    if (selectedCorpusNames.length === corpora.length) {
      corporaLabel += ' All ';
    }
    corporaLabel += (selectedCorpusNames.length === 1)
      ? selectedCorpusNames + ' Corpus'
      : selectedCorpusNames.length + ' Corpora';

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
          <Corpora
            corpora={this.props.corpora}
            selectedCorpusNames={this.props.selectedCorpusNames}
            toggleCorpora={this.props.toggleCorpora} />
        </div>
      </Modal>;
    return <ModalTrigger trigger='click' modal={overlay}>
      <a className="corpora-modal-trigger"><Glyphicon glyph="cog"/> {this.renderCorporaLabel()}</a>
    </ModalTrigger>;
  }
});

module.exports = CorpusSelector;
