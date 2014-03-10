package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * Induces a lexicon for a CCG semantic parser directly from 
 * sentences with annotated logical forms.
 *  
 * @author jayant
 */
public class BatchLexiconInduction {
   
  private final int numIterations;
  private final boolean allowComposition;
  private final boolean allowWordSkipping;
  private final boolean normalFormOnly;
  private final CcgFeatureFactory featureFactory;

  private final List<CcgBinaryRule> binaryRules;
  private final List<CcgUnaryRule> unaryRules;

  private final GradientOptimizer trainer;
  private final LexiconInductionStrategy strategy;

  private final LogFunction log;

  public BatchLexiconInduction(int numIterations, boolean allowComposition,
      boolean allowWordSkipping, boolean normalFormOnly, CcgFeatureFactory featureFactory,
      List<CcgBinaryRule> binaryRules, List<CcgUnaryRule> unaryRules, GradientOptimizer trainer,
      LexiconInductionStrategy strategy, LogFunction log) {
    this.numIterations = numIterations;
    this.allowComposition = allowComposition;
    this.allowWordSkipping = allowWordSkipping;
    this.normalFormOnly = normalFormOnly;
    this.featureFactory = Preconditions.checkNotNull(featureFactory);

    this.binaryRules = Preconditions.checkNotNull(binaryRules);
    this.unaryRules = Preconditions.checkNotNull(unaryRules);
    
    this.trainer = Preconditions.checkNotNull(trainer);
    this.strategy = Preconditions.checkNotNull(strategy);
    this.log = Preconditions.checkNotNull(log);
  }

  public CcgParser induceLexicon(List<CcgExample> examples) {
    Set<LexiconEntry> currentLexicon = Sets.newHashSet();
    ParametricCcgParser parserFamily = null;
    SufficientStatistics parameters = null;
    CcgParser parser = null;
    // Get all of the part-of-speech tags used in the examples.
    Set<String> posTags = CcgExample.getPosTagVocabulary(examples);

    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);
      log.startTimer("propose_entries");
      Set<LexiconEntry> proposedEntries = Sets.newHashSet();
      for (CcgExample example : examples) {
        Set<LexiconEntry> exampleProposals = strategy.proposeLexiconEntries(example, parser);
        proposedEntries.addAll(exampleProposals);
      }
      log.stopTimer("propose_entries");

      currentLexicon.addAll(proposedEntries);
      log.logStatistic(i, "num_proposed_lexicon_entries", proposedEntries.size());
      log.logStatistic(i, "lexicon_size", currentLexicon.size());

      parserFamily = ParametricCcgParser.parseFromLexicon(currentLexicon,
          binaryRules, unaryRules, featureFactory, posTags, allowComposition, null,
          allowWordSkipping, normalFormOnly);
      SufficientStatistics newParameters = parserFamily.getNewSufficientStatistics();
      if (parameters != null) {
        newParameters.transferParameters(parameters);
      }
      parameters = newParameters;

      // Train the parser with the current parameters.
      log.startTimer("optimization");
      GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(parserFamily, 100);
      parameters = trainer.train(oracle, parameters, examples);
      parser = parserFamily.getModelFromParameters(parameters);
      log.stopTimer("optimization");
      log.notifyIterationEnd(i);
    }
    System.out.println(parserFamily.getParameterDescription(parameters));

    return parser;
  }
}
