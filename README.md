# jklol
Jayant Krishnamurthy's (Machine) Learning and Optimization Library

A machine learning library with many different kinds of models, such as:

* Graphical models
* Sequence models
* Context-free grammars
* <a href="src/com/jayantkrish/jklol/ccg">Combinatory Categorial Grammar semantic parsers</a>

These models are built using useful core primitives for learning and
inference:

* Optimization -- stochastic gradient, Adagrad, LBFGS, and Expectation Maximization.
* Tensor math -- supports both sparse and dense tensors.

## Installation

### Git 

Get jklol by cloning the git repository:

    git clone https://github.com/jayantk/jklol.git

The preferred way to build jklol is using <a
href="http://www.scala-sbt.org/">sbt</a>. In the root directory, run:

    sbt package

This command will compile the source and produce
`target/jklol-(version).jar`. If you don't have sbt, you can also build jklol using ant:

    ant jar

This command will produce `jklol.jar` in the root directory.

### Central Repository

If you wish to use jklol as a library, you can get jklol through the
Maven central repository. The group id is "com.jayantkrish.jklol" and
the artifact id is "jklol". The latest version can be found by <a
href="https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.jayantkrish.jklol%22%20AND%20a%3A%22jklol%22">searching
the central repository</a>. Maven users can add the following to the
dependency section of pom.xml:

    <dependency>
	  <groupId>com.jayantkrish.jklol</groupId>
      <artifactId>jklol</artifactId>
      <version>1.2</version>
	</dependency>

Sbt users can add the following to their library dependencies:

	"com.jayantkrish.jklol" % "jklol" % "1.2"

