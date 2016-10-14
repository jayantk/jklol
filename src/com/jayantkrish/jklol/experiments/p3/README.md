# Parsing to Probabilistic Programs (P3) Experiments on Scene

This package contains code for running P3 on the <a
href="http://rtw.ml.cmu.edu/tacl2013_lsp/">Scene data set</a>.

## Running the Experiment

First, <a href="https://github.com/jayantk/jklol">follow the
installation and build instructions</a>. Next, download and extract
the scene data set. From the root jklol directory, run:

	cd experiments/p3/scene
	wget http://rtw.ml.cmu.edu/tacl2013_lsp/scene.tar.gz
	tar xvzf scene.tar.gz
	
These commands will download the data and extract it to the
`experiments/p3/scene/scene` directory. In this directory, you will see
several subdirectories with names like `000000` containing the data for
each environment. The raw training data contains extra annotations
that need to be preprocessed. From the root jklol directory, run:

	./experiments/p3/scripts/fix_training.sh

To train and evaluate P3, run:

	./experiments/p3/scripts/run_experiment.sh
	
By default, this script performs a single fold of cross-validation and
trains P3 without annotated environments. It takes a few minutes and
sends its output to the `experiments/p3/scene/output/default/`
directory. Results for each held-out environment are stored in their
corresponding subdirectories. After the command finishes, it will
print out evaluation results:

	> Results for: default
	> Training accuracy: 0.834615 (217 / 260)
	> Test accuracy: 0.791667 (19 / 24)

Note that these numbers are only for a single CV fold, which in this
case holds out environment `000000`. A training log, training/test
predictions, and serialized models are generated for each
cross-validation fold in the
`experiments/p3/scene/output/default/<foldname>` directories.

To run all folds of cross-validation (in parallel), edit
`experiments/p3/scripts/config.sh`. Comment out `NUM_FOLDS=1` and
uncomment the line above it. Note that this requires a large amount of
RAM. To train P3 with annotated environments, edit
`experiments/p3/scripts/train.sh` and add the `--worldFilename` flag
to `CMD`. (The correct option value is shown in a comment in the
file.)
