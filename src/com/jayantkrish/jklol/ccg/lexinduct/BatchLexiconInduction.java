package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;

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
  
  public BatchLexiconInduction(int numIterations, boolean allowComposition,
      boolean allowWordSkipping, boolean normalFormOnly, CcgFeatureFactory featureFactory,
      List<CcgBinaryRule> binaryRules, List<CcgUnaryRule> unaryRules, GradientOptimizer trainer) {
    this.numIterations = numIterations;
    this.allowComposition = allowComposition;
    this.allowWordSkipping = allowWordSkipping;
    this.normalFormOnly = normalFormOnly;
    this.featureFactory = Preconditions.checkNotNull(featureFactory);

    this.binaryRules = Preconditions.checkNotNull(binaryRules);
    this.unaryRules = Preconditions.checkNotNull(unaryRules);

    this.trainer = Preconditions.checkNotNull(trainer);
  }
  
  public CcgParser induceLexicon(List<CcgExample> examples) {
    Set<LexiconEntry> currentLexicon = Sets.newHashSet();
    SufficientStatistics parameters = null;
    CcgParser parser = null;
    // Get all of the part-of-speech tags used in the examples.
    Set<String> posTags = CcgExample.getPosTagVocabulary(examples);

    for (int i = 0; i < numIterations; i++) {
      Set<LexiconEntry> proposedEntries = Sets.newHashSet();
      for (CcgExample example : examples) {
        proposedEntries.addAll(proposeLexiconEntries(example, parser));
      }
      currentLexicon.addAll(proposedEntries);

      ParametricCcgParser parserFamily = ParametricCcgParser.parseFromLexicon(currentLexicon,
          binaryRules, unaryRules, featureFactory, posTags, allowComposition, null,
          allowWordSkipping, normalFormOnly);
      SufficientStatistics newParameters = parserFamily.getNewSufficientStatistics();
      if (parameters != null) {
        newParameters.transferParameters(parameters);
      }
      parameters = newParameters;

      // Train the parser with the current parameters.
      GradientOracle<CcgParser, CcgExample> oracle = new CcgPerceptronOracle(parserFamily,
          new CcgBeamSearchInference(null, 100, -1, Integer.MAX_VALUE, 1, false), 0.0);
      parameters = trainer.train(oracle, parameters, examples);
      parser = parserFamily.getModelFromParameters(parameters);
    }

    return parser;
  }
  
  private Set<LexiconEntry> proposeLexiconEntries(CcgExample example, CcgParser parser) {
    if (parser == null) {
      Preconditions.checkArgument(example.hasLogicalForm());
      List<Set<String>> assignment = Lists.newArrayList();
      assignment.add(Sets.<String>newHashSet());
      CcgCategory category = new CcgCategory(HeadedSyntacticCategory.parseFrom("S{0}"),
          example.getLogicalForm(), Collections.<String>emptyList(),
          Collections.<Integer>emptyList(), Collections.<Integer>emptyList(), assignment);

      LexiconEntry entry = new LexiconEntry(example.getSentence().getWords(), category);
      return Collections.singleton(entry);
    } else {
      // TODO
      return Sets.newHashSet();
    }
  }
}
