# Query Suggestion

## Functionality

This module powers the 'Suggest Query' feature on okcorpus. It takes as input a query and a table, as output it 
produces number of new queries to suggest to the user. The suggestions are made by finding variations of the input 
query that would perform well on the pre-existing entries in the given table.

### Suggestion Types
Two variations of this operation are supported. 'Narrowing' makes the given query more specific (so the suggested 
queries will match less sentences than original query), and 'Broadening' makes the given query broader (so the 
suggested queries will match more sentences than the original query).

### Limitations
Currently only fixed length queries are supported, which means queries that contain control
 characters like '*' or '+' are not supported.
Only one column tables are supported.
Currently only the following changes can be made to a user's query
1. Adding a prefix to the original query (ex. "fat cat" => "the fat cat")
2. Adding a suffix to the original query (ex. "fat cat" => "fat cat ran")
3. Replace a token in the original query (ex. "fat cat" => "fat NN")
4. Replacing a token with a disjunction (ex. "fat cat" => "{fat, lazy} cat")

prefixes, suffixes, or new tokens can be words, clusters, or part of speech tags.

## Implementation
We view the task of suggesting queries as the task of finding 'Query Operators', or functions that takes as 
input a query and outputs a new query, that produces a good query for when applied to the original query. Here
'good' is measured heuristically by evaluating the queries relative to the existing entries in the table.

Finding these operators requires three steps:

1. Subsampling. In this step we try to find a sample of sentences that are:
    1. 'Close' to the original query, in other words sentences that might match a query we might consider suggesting to the user
    2. Has a good mix of positive, negative, and unlabelled examples
2. Generating 'primitive query operations'. This phase generates a collection of query operations that make small, 
incremental changes to the original query (for example replacing a single token in the original query).
3. Search for the best best way to combine the primitive operations into a 'compound operation'. This is done by incrementally building up
larger and larger combinations of primitive operations using a beam search. This requires two subcomponents:
    1. An evaluation function, that produces a score for a given query indicating how good that query is
    2. A 'combiner' that knows how to combined the primitive operations, and which primitive operations are
       compatible and so can be applied to the same query.

When evaluating queries we avoid running them on the index, this would take too much time since 
we need to evaluate thousands of queries.
Instead we keep track, for each primitive query operation, which sentences from our subsample 
would match our query if the primitive operation is applied to it. So, for example, if we are using 
a primitive operation that adds 'the' as a prefix to the original query, and our original query
was 'cat', we record which sentences from our subsample includes the full phrase 'the cat'. For operations
that combine several primitive operations we can then take the intersection of the sentences each
 primitive operation matches to find out what sentences the combined operation would match.
 
In addition to which sentences a primitive operation would allow the initial query to match, we also mark which 
sentences a primitive operation is 'required' for. These sentences are ones that the initial query will
not match unless the primitive operation is applied to it. For example, if the initial query is 'the cat' and our
primitive operation is replaces 'the' with 'DT' the primitive operation is required 
for the sentence
'a cat' and not required for the sentence 'the cat'. When making 'broadening' query suggestion we
keep track of 
number of edits would be needed to make made to the user's query to match each sentences, and then track the number
of 'required' operators that were applied to each sentence to determine if the query would match the starting
sentence.
 

## Code
1. subsample package handles the subsampling phase.
2. primitiveop package defines what a primitive query operation is and how to generate them from a subsample.
3. compoundop package defines how primitive query operations can be combined into compound operations.
4. QueryEvaluator.scala defines the evaluation functions to use.
5. TokenizedQuery.scala breaks queries into sequences of smaller queries as a preprocessing step.
6. QuerySuggester.scala 'glues' these pieces together and implements the actual beam search.
