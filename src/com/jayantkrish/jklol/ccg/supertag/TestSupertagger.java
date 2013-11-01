package com.jayantkrish.jklol.ccg.supertag; 

import java.util.Iterator;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cli.TrainCcg;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.sequence.TaggerUtils.SequenceTaggerError;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSupertagger extends AbstractCli {
  
  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  
  private OptionSpec<String> syntaxMap;
  private OptionSpec<Double> multitagThreshold;
  private OptionSpec<Double> pruneLowProbabilityTransitions;
  
  public TestSupertagger() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
    
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    multitagThreshold = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class);
    pruneLowProbabilityTransitions = parser.accepts("pruneLowProbabilityTransitions")
        .withRequiredArg().ofType(Double.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    Supertagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model), Supertagger.class);

    if (options.has(pruneLowProbabilityTransitions)) {
      trainedModel = pruneLowProbabilityTransitions(trainedModel,
          options.valueOf(pruneLowProbabilityTransitions));
    }

    if (options.has(testFilename)) {
      List<CcgExample> ccgExamples = TrainCcg.readTrainingData(options.valueOf(testFilename), true,
          true, options.valueOf(syntaxMap));

      List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> testData = 
          TrainSupertagger.reformatTrainingExamples(ccgExamples, false);

      SequenceTaggerError error = null;
      if (!options.has(multitagThreshold)) {
        error = TaggerUtils.evaluateTagger(trainedModel, testData);
      } else {
        error = TaggerUtils.evaluateMultitagger(trainedModel, testData, options.valueOf(multitagThreshold));
      }

      System.out.println("TAG ACCURACY: " + error.getTagAccuracy() + " (" + error.getNumTagsCorrect() + " / " + error.getNumTags() + ")");
      System.out.println("SENTENCE ACCURACY: " + error.getSentenceAccuracy() + " (" + error.getNumSentencesCorrect() + " / " + error.getNumSentences() + ")");
      System.out.println("TAGS PER TOKEN: " + error.getTagsPerItem());
    } else {
      // TODO.
      
    }
  }
  
  private static Supertagger pruneLowProbabilityTransitions(Supertagger trainedModel,
      double transitionPruningThreshold) {
    FactorGraphSupertagger fgTagger = (FactorGraphSupertagger) trainedModel;
    DynamicFactorGraph fg = fgTagger.getInstantiatedModel();
    PlateFactor transitionFactor = fg.getFactorByName(TaggerUtils.TRANSITION_FACTOR);
    if (transitionFactor != null) {
      DiscreteFactor factor = transitionFactor.getFactor().coerceToDiscrete();
      VariableNumMap nextLabel = factor.getVars().getVariablesByName(TaggerUtils.NEXT_OUTPUT_PATTERN);
      DiscreteFactor partitionFunction = factor.marginalize(nextLabel);

      // Get the probability of each transition.
      DiscreteFactor transitionProbabilities = factor.product(partitionFunction.inverse());
      DiscreteFactor maxTransitionProbability = transitionProbabilities
          .maxMarginalize(nextLabel.getVariableNums());

      System.out.println(partitionFunction.describeAssignments(partitionFunction.getMostLikelyAssignments(-1)));
      System.out.println(transitionProbabilities.describeAssignments(transitionProbabilities.getMostLikelyAssignments(100)));

      DiscreteFactor transitionThreshold = maxTransitionProbability.product(
          transitionPruningThreshold);

      // Build a new table factor containing only the transitions with high enough probability.
      TableFactorBuilder builder = new TableFactorBuilder(factor.getVars(), SparseTensorBuilder.getFactory());
      Iterator<Outcome> iter = transitionProbabilities.outcomeIterator();
      while (iter.hasNext()) {
        Outcome outcome = iter.next();
        Assignment assignment = outcome.getAssignment();
        if (outcome.getProbability() >= transitionThreshold.getUnnormalizedProbability(assignment)) {
          builder.setWeight(assignment, factor.getUnnormalizedProbability(assignment));
        }
      }
      TableFactor newFactor = builder.build();
      System.out.println("Initial # of transitions: " + factor.size());
      System.out.println("Pruned # of transitions: " + newFactor.size());

      List<PlateFactor> newFactorList = Lists.newArrayList(fg.getPlateFactors());
      boolean foundFactor = false;
      for (int i = 0; i < newFactorList.size(); i++) {
        if (newFactorList.get(i) == transitionFactor) {
          newFactorList.set(i, new ReplicatedFactor(newFactor, ((ReplicatedFactor) transitionFactor).getPattern()));
          foundFactor = true;
        }
      }
      Preconditions.checkState(foundFactor);

      DynamicFactorGraph newFactorGraph = new DynamicFactorGraph(fg.getVariables(), newFactorList, fg.getFactorNames());
      FactorGraphSupertagger newTagger = new FactorGraphSupertagger(fgTagger.getModelFamily(),
          fgTagger.getParameters(), newFactorGraph, fgTagger.getFeatureGenerator(), fgTagger.getInputGenerator());
      return newTagger;
    } else {
      // No transitions to prune
      System.out.println("WARNING: no transitions to prune.");
      return trainedModel;
    }
  }

  public static void main(String[] args) {
    new TestSupertagger().run(args);
  }
}
