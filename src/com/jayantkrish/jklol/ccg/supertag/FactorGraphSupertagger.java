package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Function;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.MultitaggedSequence;

/**
 * A supertagger using a graphical model.
 * 
 * @author jayantk
 */
public class FactorGraphSupertagger extends 
FactorGraphSequenceTagger<WordAndPos, HeadedSyntacticCategory> implements Supertagger {

  private static final long serialVersionUID = 2L;

  
  public FactorGraphSupertagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator,
      Function<? super LocalContext<WordAndPos>, ? extends Object> inputGen,
      MarginalCalculator maxMarginalCalculator, JunctionTree marginalCalculator,
      WordAndPos startWordAndPos, HeadedSyntacticCategory startCategory) {
    super(modelFamily, parameters, instantiatedModel, featureGenerator, inputGen,
        HeadedSyntacticCategory.class, maxMarginalCalculator, marginalCalculator, startWordAndPos,
        startCategory);
  }

  @Override
  public SupertaggedSentence multitag(List<WordAndPos> input, double threshold) {
    MultitaggedSequence<WordAndPos, HeadedSyntacticCategory> sequence = super.multitag(input, threshold);
    return new SupertaggedSentence(sequence.getItems(), sequence.getLabels(), sequence.getLabelProbabilities());
  }
}
