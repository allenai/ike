var React = require('react');
var WordData = React.createClass({                                              
  render: function() {                                                          
    var attrs = this.props.attributes;                                          
    var word = this.props.word;                                                 
    var createAttr = function(name, index) {                                    
      return (<li key={name}>{name} = {attrs[name]}</li>);                      
    };                                                                          
    return (                                                                    
      <div className="wordData">                                                
        <div className="word">{word}</div>                                      
        <ul className="wordAttributes">{Object.keys(attrs).map(createAttr)}</ul>
      </div>                                                                    
    );                                                                          
  }                                                                             
});

module.exports = WordData;
