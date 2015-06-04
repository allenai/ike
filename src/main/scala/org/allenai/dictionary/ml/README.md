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
For 'narrowing' the following changes can be made to a user's query
1. Adding a prefix to the original query (ex. "fat cat" => "the fat cat")
2. Adding a suffix to the original query (ex. "fat cat" => "fat cat ran")
3. Replace a token in the original query (ex. "fat cat" => "fat NN")
4. 1-3 can also suggest disjunctions (ex. "fat cat" => "{fat, lazy} cat", "NN" => "{cat, dog}")
5. Changing the number of repetitions (ex "cat*" => "cat", or "a[1,5]" -> "a[2,3]")
6. Removing a starred token (ex "the cat*" => "the")
7. Changing a star to a plus (ex "cat*" => "cat+")
9. In some cases, applying 1-4 for repeated ops (ex "{cat, dog}[1,3]" => "cat[1,2]")
10. Make a QSimilarPhrase less narrow ex "dog~10" => "dog~5")

For 'broadening' operations are more limited, in particular we currently do not support 
suggestions for QExpr's inside repetitions. Note we can still make suggestions for queries that
 contain repetitions, we just will not be able to suggest any changes to the repetition.
1. Replacing a token within the original query (ex. "cat" => "NN")
3. Adding a token to make a disjunction within the query (ex "cat" => "{cat, dog}")
3. Adding a token to a disjunction (ex "{dog, cat}" => "{cat, dog, cow}")
4. Changing a QSimilarPhrase (ex "dog~10" => "dog~20")


## Implementation
We view the task of suggesting queries as the task of finding 'Query Operators', or functions that takes as 
input a query and outputs a new query, that produces a good query for when applied to the original query. Here
'good' is measured heuristically by evaluating the queries relative to the existing entries in the table.

Finding these operators requires four steps:

1. Query tokenizing, we break the input query into a sequence of 'tokens'. For example, 
"a {b, c} d*" would get tokenized into "a", "{b, c}", "d*"
2. Subsampling. In this step we try to find a sample of sentences that are:
    1. 'Close' to the original query, in other words sentences that might match a query we might consider suggesting to the user
    2. Has a bias to containing labelled examples, we do this since if a query is very general
       ex. (VBG NN) it will much almost nothing but unlabelled examples, making it hard to 
       evaluate possible suggestions effectively
2. Generating 'primitive query operations'. This phase generates a collection of query operations that make small, 
incremental changes to the original query by altering a single token in the tokenized query.
3. Search for the best best way to combine the primitive operations into a 'compound operation'. This is done by incrementally building up
larger and larger combinations of primitive operations using a beam search. This requires two subcomponents:
    1. An evaluation function, that produces a score for a given query indicating how good that query is
    2. A 'combiner' that knows how to combined the primitive operations, and which primitive operations are
       compatible and so can be applied to the same query.

The beam search component requires the we evaluate a large number of queries, doing this 
quickly can be a challenge. Running each query using can take too much time, especially for long,
complex queries. It would also be highly redundant (for example we might end up running 
cat~1, cat~2... cat~100), which is clearly inefficient since we evaluating/running almost the same 
query each time.
 
The solution to this problem that I have come up with is to build up a sample of Hits before 
hand, and then pre-compute information about how our primitive operations would change which of 
those Hits the starting query would match. Since the primitive operations effect different 
tokens, if multiple primitive operations are combined (which we call a 'compound operator') we can 
compute which Hits applying that compound operator to the starting query will allow us to match 
using only the precomputed information. Although efficient, this approach has resulted in 
requiring a large amount of booking.

In more detail, for each primitive operation we track which sentences from our subsample 
would match our query if the primitive operation is applied to it. So, for example, if we are using 
a primitive operation that adds 'the' as a prefix to the original query, and our original query
was 'cat', we record which sentences from our subsample includes the full phrase 'the cat'. For operations
that combine several primitive operations we can then take the intersection of the sentences each
 primitive operation matches to find out what sentences the combined operation would match. For 
 example if our query is "DT NN" and our subsample is:
 1. "a cat"
 2. "the cat"
 3. "the dog"
 4. "dog dog"

We would record that changing the "DT" -> "the" means the query will only Hit sentences 2 and
 3, changing the "NN" -> "cat" means that the query will only match sentences 1 and 2. The we can
  deduce that if we applied both these transformations the query would only match: {1,2} 
  INTERSECTION {2,3} = {2}. 
   
Although this is lets us evaluate a lot of queries efficiently, precomputing how different 
primitive operators effect what Hits the query will continue to match can be challenging for 
certain kinds of queries and is the main source of the limitations of the suggestions we can make.
 
Broadening queries is slightly more complex. To do this will build a 'generalized' version of the
 query to build up our sample (for example we might search for "DT {NN,NNS,NNP,NNPS}" for the 
 query "the NN"). A complication that then emerges is that no single 'primitive operation' might 
 be sufficient for the starting query to match one of our samples. Using the same example, 
 the sentence "a dogs" could be returned by our broadened query, but we would need to change 
 two tokens within our starting query to match that sentence. 
 
To deal with this issue we additionally track the edit distance between a query and a sentence. 
We define the edit distance between a query and a sentence to be the number of query-tokens that 
would need to be altered for  the query to match that sentence For example, if our query is "a 
fast horse", to make the query  match the phrase "a slow horse" we will need to edit at least one
 of the symbols in that query, (for example, replace the word 
"slow" in the query with the word "fast") so the edit distance is one. The distance from the same 
query to the sentence "a slow cow" would be 2. 

During subsampling we record the edit distance from our query of each sentence in our sample.
In addition, we keep track of which operators are capable of reducing the edit distance from our 
query to each sentence, operators that can reduce the edit distance are called "required" operators.
 We can use this information to figure out which operations would allow the starting query to match 
 which sentence in our sample.

To give a more detailed example consider the following scenario:

Our starting query is "a cat"

Our query operation replaces "a" with "DT" (so our new query would be "DT cat")

We have three sentences:
1. "a cat"
2. "the cat"
3. "the dog"
4. "dog dog"

Now the distance from our query to each sentence is:
* 0 for sentence 1
* 1 for sentence 2
* 2 for sentence 3
* 2 for sentence 4
   
And our operator "a" -> "DT"
* matches but is not required for sentence 1
* matches and fills a requirement for sentence 2
* matches and fills a requirement for sentence 3
* does not match sentence 4

It is left to the compound operators to calculate the number of required operators for each 
sentences that will be made by applying all their primitive operators, and in particular to avoid
"double counting" requirements if multiple operators would, for example, edit the same query-token
within a query.
 
Implementation-wise we record this information using an map of integers to integers, where the keys 
are integers corresponding to particular sentences and the values are the number of required
edits made to that sentence.
By comparing the number of requirement a operator has fulfilled for a
particular sentence to the edit distance from the starting query to that sentence we can quickly 
deduce whether the starting query would match then sentence if that set of operators were applied. 
In the above example this means the operator would be associated with:

IntMap(1 -> 0, 2 -> 1, 3 -> 1)

We use IntMap rather then Seq\[(Int, Int)\] or Map\[Int, Int\] because:
1. It will have a sparse representation, and we expect many of the rules we examine to be sparse
2. It is optimized for merges (unions and intersections), operations we do frequently

## Code
1. subsample package handles the subsampling phase.
2. queryop package defines what a primitive query operation is and how to generate them from a 
subsample.
3. compoundop package defines how primitive query operations can be combined into compound operations.
4. QueryEvaluator.scala defines the evaluation functions to use.
5. TokenizedQuery.scala breaks queries into sequences of smaller queries as a preprocessing step.
7. HitAnalyzer.scala preprocesses Hits by calculating their labels and grouping the tokens
within each hit so that they can be used by queryop.
6. QuerySuggester.scala glues these pieces together and implements the actual beam search.
7. QueryGeneralizer.scala is used to decide ways in which query-tokens can be generalized, we use 
this when building a sample for broadening queries.