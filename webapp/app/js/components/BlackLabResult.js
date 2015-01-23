var React = require('react');
var WordDataSeq = require('./WordDataSeq.js');
var CaptureGroup = require('./CaptureGroup.js');
var BlackLabResult = React.createClass({                                        
  render: function() {                                                          
    var result = this.props.result;                                             
    var seq = result.wordData;                                                  
    var matchOffset = result.matchOffset;                                       
    var matchSeq = seq.slice(matchOffset[0], matchOffset[1]);                   
    var groupNames = Object.keys(result.captureGroups);                         
    var createGroup = function(name) {                                          
      var offsets = result.captureGroups[name];                                 
      var groupSeq = seq.slice(offsets[0], offsets[1]);                         
      var key = result.id + name;                                               
      return <CaptureGroup name={name} groupSeq={groupSeq} key={key}/>;         
    };                                                                          
    return (                                                                    
      <tr className="blackLabResultRow">                                        
      {groupNames.map(createGroup)}                                             
      <td className="resultContext"><WordDataSeq data={seq}/></td>              
      </tr>                                                                     
    );                                                                          
  }                                                                             
});
module.exports = BlackLabResult;
