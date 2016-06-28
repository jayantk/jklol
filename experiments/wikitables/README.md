This folder contains scripts and data for experiments on WikiTable
Questions.

The WikiTable data itself can be downloaded here:

http://www-nlp.stanford.edu/software/sempre/wikitable/

The zip file should be placed in the experiments/wikitables/ and
unzipped, creating the experiments/wikitables/WikiTableQuestions
directory.

TODO:
1. Better mention matching -- substrings of table values, numbers mentions as
   text (four, first, etc.), number mentions as numbers
  * need to identify informative overlaps of table value substrings. bit tricky.
2. Row operations of next/prev don't seem well matched to data
3. Better tooling (CLIs)
  * enumerate LFS for a particular question on a particular table
  * execute LF on a particular table
  * parallel enumeration on many machines
4. formalism:
  * greater than, less than, equal
  * argmax / argmin on number valued things (Also: "player who ranked the most."?? -- frequency in set?)
5. fix mention reuse for headings & values
6. table elements as numbers

(>/=/< :c i)
(max/min :e :<e,i>)
(relate $0:c $1:c) = (intersect $0:c (samerow-set $1:c))

== Examples ==

what year was the first to reach 1,000 or more live births? [1985] csv/203-csv/668.csv
(first-row (intersect (column-id-set 0) (samerow-set (> (column "Live births") 1000))))

which opponent has the most wins [Bahrain] csv/204-csv/836.csv
(max (values (column-set "Opponent")) (lambda (x) (set-size (intersect (samerow-set (cellvalue-set x)) (cellvalue-set "Lost")))))

the team's record in 2011 was the same was it's record in what year [2009] csv/204-csv/32.csv
(= (relate (column-set "W–L") (cellvalue-set "2011")) (column-set "W–L"))

(get "Season" (same "W-L" (cellvalue-set "2011")))

which players played the same position as ardo kreek? [Siim Ennemuist, Andri Aganits] csv/203-csv/116.csv
(get "Player" (same "Position" (cellvalue-set "Ardo Kreek")))

who was the opponent in the first game of the season? [Derby County] csv/204-csv/495.csv
(get "Opponent" (first-row all-rows))

how many people stayed at least 3 years in office? [4] csv/203-csv/705.csv
(count (> (derived-property "years") 3)

== type system ==

entity: e
set of entities: s(e)
row: r
set of rows: s(r)
col: c
set of cols: s(c)
i: integer

== operations ==)

Set monad: (restricted monad to types that are comparable for equality, in our case all of them)
return: A -> s[A]
bind: A->s[B] s[A] -> s[B]
argmax/min: s[A] (A->i) -> A  (or error)
the: s[A] -> A  (or error)
count: s[A] -> i
max/min/sum/avg: s[i] -> i
intersection/union: s[A] s[A] -> s[A]

Maps: (technically multimaps)
identity: s[A] -> t[A,A]
apply: B->C t[A,B] -> t[A,C]
intersect-values: s[B] t[A,B] -> t[A,B]  (could be done with partial application of binary operations)
argmax/min: t[A,i] -> A

Table operations:
e2r : c, e -> r  (entity to row, via a column c)
r2e : c, r -> e  (row to entity, via a column c)
entities-with-value: string -> s[e]
next/prev-row: r -> s[r]  (row may not exist, hence s[])
row-index: r -> i


(lift1 (e2r column))

(rows-to-entities (column "Year") (argmax (entities-to-rows (column "League") (cells-with-value "a-league")) row-index))

(argmax (entities-to-rows (column "League") (cells-with-value "a-league")) (rows-to-entities (column "Year")))


maps:
to-map: s[X] -> s[(X,X)]
map-apply: X->Y s[(X,X)] -> s[(X,Y)]

State whose smallest city has the largest population

state -> to-map -> (state, state) -> loc -> (state, loc.state) -> to-map
-> (state, (loc.state, loc.state)) -> population -> (state, (loc.state, population.loc.state))
-> argmin -> (state, (argmin loc.state, population.loc.state)) -> argmax 

1. state -- table of states
2. (state, state) -- identity map on states
   Representation for LF: (state, \lambda x.x)
3. (state, loc.state) -- map from states to the set of cities contained in them
   Representation for LF: (state, \lambda x. loc.x)
4. (state, (loc.state, loc.state)) -- map from each state to an identity map on the cities in that state
   Representation for LF: (state, \lambda x. (loc.x, \lambda y.y))
5. (state, (loc.state, population.loc.state)) -- map from each state s to a map m(s). m(s) maps the
   cities in s to their populations
   Representation for LF: (state, \lambda x. (loc.x, \lambda y. population.y))
6. (state, (argmin loc.state, population.loc.state)) -- map from each state to its minimum population city
   Representation for LF: (state, \lambda x. (argmin loc.x, \lambda y. population.y))
   Type is (state, city)
7. (state, population.(argmin loc.state, population.loc.state))
8. arg

Biggest city in the smallest state
city -> to-map -> (city, city) -> loc -> (city, loc.city) -> to-map 
-> (city, (loc.city, loc.city)) -> population -> (city, (loc.city, pop.loc.city))
-> argmin -> (city, (argmin loc.city pop.loc.city))
