# Lexicon learning 

This package implements the PAL algorithm for lexicon learning. 

## Usage

There are three steps required to run PAL on a data set. Each step is
illustrated with <a
href="https://github.com/jayantk/jklol/blob/master/src/com/jayantkrish/jklol/experiments/geoquery/GeoqueryInduceLexicon.java">example
code applying PAL to Geoquery</a>.

1. Create training examples -- Training data for PAL is provided as
   instances of `AlignmentExample`. These can be generated from
   questions paired with sets of logical forms, as in
   `GeoqueryInduceLexicon.readTrainingData`. When generating these
   examples, there are various parameters that can be adjusted to
   control the possible splits of logical forms.

2. Train PAL -- This step creates a `ParametricCfgAlignmentModel` and
   running EM to estimate its parameters. See example code in
   `GeoqueryInduceLexicon.trainAlignmentModel`. The parameters of this
   step can be adjusted to run the concave or coupled models from the
   paper. The output of this step is a `CfgAlignmentModel`.

3. Generate a lexicon -- Run `CfgAlignmentModel.generateLexicon` on
   the training examples to generate lexicon entries. These lexicon
   entries can be used to train a CCG semantic parser, as in
   `GeoqueryInduceLexicon.runFold`.

## Publications

If you use the PAL algorithm, please cite the following paper:

Jayant Krishnamurthy. Probabilistic Models for Learning a Semantic
Parser Lexicon. NAACL 2016
