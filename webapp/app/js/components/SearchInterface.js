var React = require('react');
var SearchInterface = React.createClass({
  getInitialState: function() {
    return {query: "", limit: 100};
  },
  handleSubmit: function(e) {
    e.preventDefault();
    this.props.callback(this.state);
  },
  onChange: function(e) {
    this.setState({query: e.target.value});
  },
  render: function() {
    return (
      <form className="searchInterface" onSubmit={this.handleSubmit}>
        <input onChange={this.onChange} value={this.state.query}/>
        <button>Search</button>
      </form>
    );
  }
});
module.exports = SearchInterface;
