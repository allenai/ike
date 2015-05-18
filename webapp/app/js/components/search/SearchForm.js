var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Input = bs.Input;
var Modal = bs.Modal;
var ModalTrigger = bs.ModalTrigger;
var Glyphicon = bs.Glyphicon;
var Corpora = require('../corpora/Corpora.js');
var TargetSelector = require('./TargetSelector.js');
var SuggestQueryButton = require('./SuggestQueryButton.js');
var SearchForm = React.createClass({
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
  renderCorporaModal: function() {
    var overlay = <Modal {...this.props} bsStyle='primary' title='Select Corpora to Query' animation={false}>
                    <div className='modal-body'>
                      <Corpora corpora={this.props.corpora} toggleCorpora={this.props.toggleCorpora} />
                    </div>
                  </Modal>;
    return <ModalTrigger trigger='click' modal={overlay}>
              <a className="corpora-modal-trigger"><Glyphicon glyph="cog"/> {this.renderCorporaLabel()}</a>
            </ModalTrigger>;
  },
  getQueryTextInterface: function(size, query) {
    return <Col xs={size}>
            {this.renderCorporaModal()}
             <Input
               type="text"
               placeholder="Enter Query"
               label="Query"
               valueLink={query}
               disabled={this.selectedCorpora().length == 0}>
             </Input>
           </Col>
  },
  render: function() {
    var handleSubmit = this.props.handleSubmit;
    var target = this.props.target;
    var query = this.props.query;
    var config = this.props.config;
    var makeUri = this.props.makeUri;
    var selector = <TargetSelector target={target}/>;
    if (config.value.ml.disable) {
      var queryForm =
          <Row>
            <Col xs={2}>{selector}</Col>
            {this.getQueryTextInterface(10, query)}
          </Row>
    } else {
      var queryForm =
          <Row>
            <Col xs={2}>{selector}</Col>
            {this.getQueryTextInterface(7, query)}
            <Col xs={3}>
              <SuggestQueryButton
                config={config}
                target={target}
                query={query}
                makeUri={makeUri}
                disabled={this.selectedCorpora().length == 0}
              ></SuggestQueryButton>
            </Col>
          </Row>
    }
    return (
      <div>
        <form onSubmit={handleSubmit}>
          {queryForm}
        </form>
      </div>
    );
  }
});
module.exports = SearchForm;
