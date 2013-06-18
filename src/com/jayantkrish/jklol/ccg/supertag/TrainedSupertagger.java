package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggedSequence;

/**
 * A supertagger trained using a factor graph.
 * 
 * @author jayantk
 */
public class TrainedSupertagger extends FactorGraphSequenceTagger<WordAndPos, SyntacticCategory> implements Supertagger {
  private static final long serialVersionUID = 1L;
  
  public TrainedSupertagger(ParametricFactorGraph modelFamily,
      SufficientStatistics parameters, DynamicFactorGraph instantiatedModel,
      FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator) {
    super(modelFamily, parameters, instantiatedModel, featureGenerator, SyntacticCategory.class);
  }
  
  @Override
  public SupertaggedSentence tag(List<WordAndPos> input) {
    TaggedSequence<WordAndPos, SyntacticCategory> sequence = super.tag(input);
    return new SupertaggedSentence(sequence.getItems(), sequence.getLabels());
  }
}
