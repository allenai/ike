var React = require('react');
var bs = require('react-bootstrap');
var Row = bs.Row;
var Col = bs.Col;
var Table = bs.Table;
var Panel = bs.Panel;
var HelpInterface = React.createClass({
  posTags: {
    "PRP$": "Possessive pronoun",
    "NNP": "Proper noun, singular",
    "NNPS": "Proper noun, plural",
    "WP$": "Possessive wh-pronoun",
    "WDT": "Wh-determiner",
    "WRB": "Wh-adverb",
    "CC": "Coordinating conjunction",
    "CD": "Cardinal number",
    "DT": "Determiner",
    "EX": "Existential there",
    "FW": "Foreign word",
    "IN": "Preposition or subordinating conjunction",
    "JJ": "Adjective",
    "JJR": "Adjective, comparative",
    "JJS": "Adjective, superlative",
    "LS": "List item marker",
    "MD": "Modal",
    "NN": "Noun, singular or mass",
    "NNS": "Noun, plural",
    "PDT": "Predeterminer",
    "POS": "Possessive ending",
    "PRP": "Personal pronoun",
    "RB": "Adverb",
    "RBR": "Adverb, comparative",
    "RBS": "Adverb, superlative",
    "RP": "Particle",
    "SYM": "Symbol",
    "TO": "to",
    "UH": "Interjection",
    "VB": "Verb, base form",
    "VBD": "Verb, past tense",
    "VBG": "Verb, gerund or present participle",
    "VBN": "Verb, past participle",
    "VBP": "Verb, non-3rd person singular present",
    "VBZ": "Verb, 3rd person singular present",
    "WP": "Wh-pronoun"
  },
  posRow: function(key) {
    return <tr key={key}><td>{key}</td><td>{this.posTags[key]}</td></tr>;
  },
  posTable: function() {
    var symbols = Object.keys(this.posTags);
    symbols.sort()
    var rows = symbols.map(this.posRow);
    return (
      <Table striped condensed>
        <thead>
          <th>Symbol</th>
          <th>Description</th>
        </thead>
        <tbody>
          {rows}
        </tbody>
      </Table>
    );
  },
  renderPosTags: function() {
    var ptbUrl = 'https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html';
    return (
      <div>
        <h3>Part-of-Speech Symbols</h3>
        <p>
          OkCorpus uses part-of-speech (POS) symbols 
          from <a href={ptbUrl}>the Penn Treebank</a>, reproduced below.
        </p>
        <Panel>
          {this.posTable()}
        </Panel>
      </div>
    );
  },
  renderGuideAndBugLinks: function() {
    var guideUrl = 'https://docs.google.com/document/d/1mTyuvTUNzHHapIEfg2i7NM4cp12wCD6AsTbiOxQA5tA/edit#heading=h.v4yfgk8xta6l';
    var issueUrl = 'https://github.com/allenai/okcorpus/issues';
    return (
      <div>
        <h3>Getting Help</h3>
        <p>
          If you are a new user, refer the <a href={guideUrl}>IKE Getting Started Guide</a>.
        </p>
        <p>
          File bugs, request features, and ask for help
          by creating a new issue <a href={issueUrl}>on GitHub</a>.
        </p>
      </div>
    );
  },
  querySyntax: [
    {
      name: 'Word Sequence',
      example: 'the dog',
      descr: 'Matches the word "the" followed by the word "dog"'
    },
    {
      name: 'Phrase',
      example: '"that brown dog"',
      descr: 'Matches the full phrase contained within the quotes, "that brown dog"'
    },
    {
      name: 'Part-of-Speech (POS)',
      example: 'DT dog',
      descr: 'Matches a determiner like "the" or "a" followed by the word "dog" (see below for table of POS symbols)'
    },
    {
      name: 'Chunk Tags',
      example: 'NP VP',
      descr: 'Matches a Noun Phrase followed by a Verb Phrase. Also supported: PP (Prepositional Phrase)'
    },
    {
      name: 'Unnamed Capture Groups',
      example: '(DT) dog',
      descr: 'Captures a determiner before the word "dog"' 
    },
    {
      name: 'Named Capture Groups',
      example: '(?<myGroup> DT) dog',
      descr: 'Captures a determiner before the word "dog" and names the capture group "myGroup"'
    },
    {
      name: 'Non-Capturing Groups',
      example: 'dog (?:PP NP)*',
      descr: 'Captures a repeating prepositional phrase and noun phrase group after the word "dog" without creating a capture group'
    },
    {
      name: 'Disjunctions',
      example: 'the {cat, dog}',
      descr: 'Matches the word "the" followed by either "cat" or "dog"'
    },
    {
      name: 'Disjunctions',
      example: '"the dog and the" {NN, NNS, NNP}',
      descr: 'Matches the phrase "the dog and the" followed by either a noun, a plural noun, or a proper noun (see below for table of POS symbols)'
    },
    {
      name: 'Disjunctions',
      example: '{(?<column1> NN) in (?<column2> NP), (?<column1> NN) at (?<column2> NN)}',
      descr: 'In expressions with disjuncts containing capture groups, you have to name the capture groups with corresponding column names. Unnamed capture groups will not work in this case.'
    },
    {
      name: 'Repetitions',
      example: 'JJ* dog',
      descr: 'Matches the word "dog" with zero or more adjectives before it'
    },
    {
      name: 'Repetitions',
      example: 'JJ+ dog',
      descr: 'Matches the word "dog" with one or more adjectives before it'
    },
    {
      name: 'Repetitions',
      example: 'JJ[2,4] dog',
      descr: 'Matches the word "dog" with at least two but no more than' +
       ' four adjectives before it'
    },
    {
      name: 'Repetitions',
      example: 'JJ[2,-1] dog',
      descr: 'Matches the word "dog" with at least two adjectives before it'
    },
    {
      name: 'Similar Words',
      example: 'dog~50',
      descr: 'Matches "dog" as well as the 50 words most similar to "dog"'
    },
    {
      name: 'Similar Phrases',
      example: '"information extraction"~50',
      descr: 'Matches the phrase "information extraction" as well as the 50 phrases most similar to "information extraction"'
    },
    {
      name: 'Special Characters',
      example: 'N/A',
      descr: 'Currently you cannot escape special characters in our query syntax -- this functionality will be added in the near future'
    },
    {
      name: 'Wildcards',
      example: '. dog',
      descr: 'Matches any word followed by the word "dog"'
    },
    {
      name: 'Table Query',
      example: '$colors',
      descr: 'Matches any text containing an entry in a single-column "colors" table'
    },
    {
      name: 'Table Column Query',
      example: '$fruit_colors.fruit',
      descr: 'Matches any text containing one of the entries in the "fruit" column of the "fruit_colors" table'
    },
    {
      name: 'Row-Associative Table Column Query',
      example: '$fruit_colors.color:0 $fruit_colors.fruit:0',
      descr: 'Matches any text containing a fruit color (followed by space) followed by a fruit that are present in the same row of the "fruit_colors" table in the "color" and the "fruit" column respectively'
    },
    {
      name: 'Table Expansion Query',
      example: '$fruits.fruit ~ 100',
      descr: 'Expands the "fruits" table to propose 100 closest words/phrases to the entries in the "fruit" column of the "fruits" table, and returns corpus matches with these candidates. If this is a single column table, you may just use "$fruits ~100".'
    },

  ],
  querySyntaxRow: function(row, i) {
    return (
      <tr key={i}>
        <td>{row.name}</td>
        <td>{row.example}</td>
        <td>{row.descr}</td>
      </tr>
    );
  },
  querySyntaxTable: function() {
    var rows = this.querySyntax.map(this.querySyntaxRow);
    return (
      <Table striped condensed>
        <thead>
          <tr>
            <th style={{width: '25%'}}>Name</th>
            <th style={{width: '25%'}}>Example</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>{rows}</tbody>
      </Table>
    );
  },
  renderQuerySyntax: function() {
    return (
      <div>
        <h3>Query Syntax</h3>
        <Panel>{this.querySyntaxTable()}</Panel>
      </div>
    );
  },
  generalConfigOptions: [
    {
      option: 'Max Rows',
      descr: 'Maximum number of unique results per query'
    },
    {
      option: 'Evidence Per Result Group',
      descr: 'Number of corpus hits for which source text is included in provenance'
    }
  ],
  querySuggestConfigOptions: [
    { option: 'Disable',
      descr: 'Hide Narrow/Broaden buttons on the Search page'
    },
    {
      option: 'Max Number of Edits',
      descr: 'Maximum number of transformations of the input query to consider for producing new query suggestions'
    },
    {
      option: 'Beam Size',
      descr: 'The Query Suggestion module uses a Beam Search over a tree of query transformations to come up with a ranked list of candidate queries. This option is used to limit the candidate set of transformations to consider at every step of the search.'
    },
    {
      option: 'Sample Size',
      descr: ' Number of corpus samples used to evaluate query suggestions'
    },
    {
      option: 'Positive Weight',
      descr: 'In evaluating query suggestions, this weight is applied to positively labeled hits for a candidate query'
    },
    {
      option: 'Negative Weight',
      descr: 'In evaluating query suggestions, this weight is applied to negatively labeled hits for a candidate query'
    },
    {
      option: 'Unlabelled Weight',
      descr: 'In evaluating query suggestions, this weight is applied to hits not present in the target table'
    }
  ],
  configOptionRow: function(row, i) {
    return (
      <tr key={i}>
        <td>{row.option}</td>
        <td>{row.descr}</td>
      </tr>
    );
  },
  configOptionsTable: function(configOptions) {
    var rows = configOptions.map(this.configOptionRow);
    return (
      <Table striped condensed>
        <thead>
          <tr>
            <th style={{width: '25%'}}>Name</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>{rows}</tbody>
      </Table>
     );
  },
  renderConfigOptions: function() {
    return (
      <div>
        <h3>Config Options</h3>
        <h4>General</h4>
        <Panel>{this.configOptionsTable(this.generalConfigOptions)}</Panel>
        <h4>Query Suggestion</h4>
        <Panel>{this.configOptionsTable(this.querySuggestConfigOptions)}</Panel>
      </div>
    );
  },
  render: function() {
    return (
      <Row>
        <Col xs={6}>
          {this.renderGuideAndBugLinks()}
          {this.renderQuerySyntax()}
          {this.renderPosTags()}
          {this.renderConfigOptions()}
        </Col>
      </Row>
    );
  }
});
module.exports = HelpInterface;
