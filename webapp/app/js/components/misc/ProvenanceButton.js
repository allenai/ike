var React = require('react/addons');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var Modal = bs.Modal;
var ModalTrigger = bs.ModalTrigger;
var TableManager = require('../../managers/TableManager.js');

var ProvenanceButton = React.createClass({
  render: function() {
    var rowvalues = this.props.rowvalues.map(TableManager.valueString)

    var title;
    if (rowvalues.length == 1)
      title = "Provenance for " + rowvalues[0]
    else
      title = "Provenance for (" + rowvalues.join(", ") + ")"

    var provenance = this.props.provenance;
    if(provenance) {
      var query = query = provenance.query;

      var examples = [];
      if (provenance.context) {
        examples = provenance.context.map(function(c, i) {
          return <p key={i}>... {c.fragment} ...</p>;
        });
      }

      var cellStyle = { "padding": "5px", "verticalAlign": "top" };
      var overlay = <Modal title={title}>
        <div className='modal-body'><table>
        <tr>
          <th style={cellStyle}>Query:</th>
          <td style={cellStyle}>{query}</td>
        </tr><tr>
          <th style={cellStyle}>Examples:</th>
          <td style={cellStyle}>{examples}</td>
        </tr>
        </table></div>
      </Modal>;

      return <ModalTrigger trigger='click' modal={overlay}>
        <Button bsSize="xsmall">
          <Glyphicon glyph="paperclip"/>
        </Button>
      </ModalTrigger>;
    } else {
      return <Button bsSize="xsmall" disabled>
        <Glyphicon glyph="paperclip"/>
      </Button>
    }
  }
});
module.exports = ProvenanceButton;
