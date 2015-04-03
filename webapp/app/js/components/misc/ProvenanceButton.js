var React = require('react/addons');
var bs = require('react-bootstrap');
var Button = bs.Button;
var Glyphicon = bs.Glyphicon;
var Popover = bs.Popover;
var OverlayTrigger = bs.OverlayTrigger;
var ProvenanceButton = React.createClass({
  render: function() {
    var provenance = this.props.provenance;

    var query = "";
    if (provenance)
      query = provenance.query;

    var examples = [];
    if (provenance && provenance.context)
      examples = provenance.context.map(function(c, i) {
        return <p key={i}>... {c.fragment} ...</p>;
      });

    var cellStyle = { "padding": "5px", "verticalAlign": "top" };
    var overlay = <Popover title='Provenance'><table>
      <tr>
        <th style={cellStyle}>Query:</th>
        <td style={cellStyle}>{query}</td>
      </tr><tr>
        <th style={cellStyle}>Examples:</th>
        <td style={cellStyle}>{examples}</td>
      </tr>
    </table></Popover>;

    return <OverlayTrigger trigger='click' placement='left' overlay={overlay}>
        <Button bsSize="xsmall">
          <Glyphicon glyph="paperclip"/>
        </Button>
      </OverlayTrigger>;
  }
});
module.exports = ProvenanceButton;
