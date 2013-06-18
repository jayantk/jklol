package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;

/**
 * A model for tagging elements of sequential data.
 *
 * @author jayantk
 * @param <I> input data type for the model (e.g., words)
 * @param <O> output data type for the model (e.g., POS tags)
 */
public interface SequenceTagger<I, O> {

  FeatureVectorGenerator<LocalContext<I>> getFeatureGenerator();
  
  TaggedSequence<I, O> tag(List<I> input);
  
  MultitaggedSequence<I, O> multitag(List<I> input, double tagThreshold);
}
