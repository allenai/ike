var xhr = require('xhr');

var SearchManager = {
  encodeQueryParameters: function(parameters) {
    var result = [];
    for (var key in parameters)
      if(parameters.hasOwnProperty(key))
        result.push(encodeURIComponent(key) + "=" + encodeURIComponent(parameters[key]));
    return result.join("&");
  },

  /* Search without capture groups */
  simpleSearch: function(
    query,
    corpora,
    resultsLimit,
    evidenceLimit,
    successCallback,
    failureCallback
  ) {
    var url = "/api/simpleSearch" + this.encodeQueryParameters({
      q: query,
      corpora: corpora.join("+"),
      rlimit: resultsLimit,
      elimit: evidenceLimit
    });

    return xhr({
      uri: url,
      method: 'GET'
    }, function(err, response, body) {
      if(response.statusCode === 200) {
        successCallback(JSON.parse(body));
      } else {
        failureCallback(response);
      }
    });
  }
};

module.exports = SearchManager;
