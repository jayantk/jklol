# jklol
Jayant Krishnamurthy's (Machine) Learning and Optimization Library

A machine learning library for lots of stuff.

## Semantic Parsing

Jklol includes a Combinatory Categorial Grammar semantic parser. This
parser can be trained to map text to lambda calculus logical
forms. The parser uses a version of CCG with incorporated head passing
and dependency markup (as in CCGbank) and also permits arbitrary
user-specified unary and binary combinators. Logical forms are
specified in lambda calculus, using LISP-like notation. The parser can
be trained to either maximize loglikelihood or margin-based objectives
using various forms of supervision. In both cases, the parser has a
rich collection of features similar to the C&C parser.


### Grammar 

Syntactic categories for this parser are specified in standard CCG
notation. However, each category and subcategory is additionally
annotated with a variable in curly braces for head passing and
dependency filling. These variables can be assigned values in the
lexicon, and unfilled dependency structures can be annotated on
them. For example, consider the following lexicon entry:

**syntax/semantics:** city := N{0} : (lambda e (pred:city e)) 

**assignment:** 0 pred:city

**unfilled dependencies:** (none)

This entry states that "city" has the syntactic category N, and
furthermore that its head, given by the variable {0}, is
"pred:city". In this example, the head is the predicate referred to
by the word; however, the head can generally be an arbitrary
string. As a more complex example, consider:

**syntax/semantics:** big := (N{1}/N{1}){0} : (lambda f e (and (pred:major e) (f e)))

**assignment:** 0 pred:major

**unfilled dependencies:** pred:major 1 1

This entry states that "big" has syntactic category N/N and that its
head is "pred:major". This entry also has an unfilled dependency that
depends on the value of the variable {1}. Furthermore, the coindexing
of the N categories means that, when this category is applied to an
argument, the head of the returned category is equal to the head of
the argument. Using this grammar, we would obtain the following parse
of "big city":

**syntax/semantics:** big city := N{1} : (lambda e (and (pred:major e) (pred:city e)))

**assignment:** 1 pred:city

**unfilled dependencies:** (none)

**filled dependencies:** pred:major 1 pred:city

As shown above, the head of the parse is "pred:city", given by the
variable {1}. Furthermore, the dependency projected by "major" that
depended on the variable {1} gets filled by its value.

### Training

The semantic parser requires three input files for training: a
lexicon, a collection of rules, and training data. A full example
experiment for training a semantic parser on GeoQuery is provided in
the experiments/geoquery directory.

The lexicon is a listing of the CCG lexicon entries used for
training. This is a comma-separated values file of the following
format:

(word),(syntax),(logical form),(assignment),(unfilled dependency),(unfilled dependency),...

The assignment and unfilled dependency entries can be repeated 0 or
more times. For example:

```
"city","N{0}","(lambda e (pred:city e))","0 pred:city"
"big","(N{1}/N{1}){0}","(lambda f e (and (pred:major e) (f e)))","0 pred:major","pred:major 1 1"
"in","((N{1}\N{1})/N{2}){0}","(lambda x f e (and (f e) (pred:in e x)))","0 pred:in","pred:in 1 1","pred:in 2 2"
```

The rules are a collection of unary and binary rules to use, in
addition to the standard application and composition
combinators. For example:

```
"N{0} NP{0}","(lambda $1 $1)"
",{0] NP{1} NP{1}","(lambda $L $R $R)"
```

The first line specifies a unary rule that applies to a N and
type-changes it to an NP. The shared variable {0} means that the head
of the N and the NP are the same. The second column is a logical form
(which must be a one-argument function) that is applied to the logical
form of the input to produce the logical form of the output. The
second line specifies a binary rule that allows an NP to absorb a
comma on the left. Similarly, the logical form is a two-argument
function here that is applied to the logical forms of the left and
right entries to produce the logical form of the output.

The training data is a collection of text with annotated logical
forms, separated by newlines:

```
city in alaska
(lambda x (and<t*,t> (city<c,t> x) (in:<lo,<lo,t>> x alaska:s)))

city in texas
...
```

Given these inputs, the semantic parser can be trained using the
following command:

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TrainSemanticParser --trainingData (training data) --lexicon (lexicon) --rules (rules) --output (location to save the trained parser)

This program has many additional configuration options, visible using
the --help flag.