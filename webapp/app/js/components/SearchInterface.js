var React = require('react');
var bs = require('react-bootstrap');
var Navbar = bs.Navbar;
var Nav = bs.Nav;
var NavItem = bs.NavItem;
var Input = bs.Input;
var Glyphicon = bs.Glyphicon;
var SearchInterface = React.createClass({
  getInitialState: function() {
    return {};
  },
  render: function() {
    var divStyle = {
    };
    return (
      <Navbar fluid>
        <div style={{marginTop:'10px'}}>
          <Input type="text" placeholder="Enter Query"/>
        </div>
      </Navbar>
    );
  }
});
module.exports = SearchInterface;
