# Combinatory Categorial Grammar Semantic Parsing

Semantic parsing is the problem of mapping natural language text to an
executable representation of its meaning known as a logical form.

This package contains a Combinatory Categorial Grammar semantic parser
along with training algorithms and utilities for manipulating logical
forms. The parser has rich and flexible parameterization and can be
trained with many different kinds of supervision.

## Example

A full semantic parsing experiment on the Geoquery data set is
provided in <a href="../experiments/geoquery">experiments/geoquery</a>
package. This example includes lexicon learning, defining parser
features, and training.

## Lexicon

Lexicon entries map words or phrases to syntactic categories and
logical forms. Syntactic categories have additional head passing and
dependency markup, as provided in syntactic CCG parsers. Logical forms
are written in lambda calculus using S-Expressions (i.e., LISP
notation). Lexicon entries can be generated programmatically or
provided in CSV format. For example:

    "population","(N:i{0}/N:e{1}){0}","(lambda $0 (population:<lo,i> $0))","0 population:<lo,i>","population:<lo,i> 1 1"

The curly braces specify head-passing rules and the final two columns
provide dependency markup. See <a
href="LexiconEntry.java">LexiconEntry</a> for details.

The lexicon can be automatically learned using the <a
href="lexinduct">PAL lexicon learning algorithm</a>.

## Logical Forms

Logical forms are represented using the <a
href="lambda2/Expression2.java">Expression2</a> class. The `lambda2`
package contains many utilities for manipulating expressions, e.g.,
reducing them. Expressions can also be executed using the LISP
evaluators in the <a href="../lisp">lisp package</a>.

## Training

The parser can be trained to either maximize loglikelihood or
margin-based objectives using various forms of supervision. Training
is done by defining a gradient oracle and passing it to an
optimization algorithm.

