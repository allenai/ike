var WordData = React.createClass({
  render: function() {
    var attrs = this.props.attributes;
    var word = this.props.word;
    var createAttr = function(name, index) {
      return (<li>{name} = {attrs[name]}</li>);
    };
    return (
      <div className={wordData}>
        <div>{word}</div>
        <ul>{Object.keys(attrs).map(createAttr)}</ul>
      </div>
    );
  }
});
