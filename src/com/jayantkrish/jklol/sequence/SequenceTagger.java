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
  
  /**
   * Runs the sequence tagger as a multitagger, generating one or more
   * labels per item in the input sequence. Each returned label has 
   * probability greater than {@code tagThreshold * (bestLabelProbability)}.
   * {@code tagThreshold} must be between 0 and 1, with values closer to 0
   * returning more labels per item.
   * 
   * @param input
   * @param tagThreshold 
   * @return
   */
  MultitaggedSequence<I, O> multitag(List<I> input, double tagThreshold);
}
