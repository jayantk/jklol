package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Default implementation of multitagged sequences.
 * 
 * @author jayant
 */
public class ListMultitaggedSequence<I, O> implements MultitaggedSequence<I, O> {
  
  private final List<I> items;
  private final List<List<O>> labels;
  private final List<List<Double>> labelProbabilities;

  public ListMultitaggedSequence(List<I> items, List<List<O>> labels,
      List<List<Double>> labelProbabilities) {
    this.items = Preconditions.checkNotNull(items);
    this.labels = Preconditions.checkNotNull(labels);
    this.labelProbabilities = Preconditions.checkNotNull(labelProbabilities);
    
    Preconditions.checkArgument(items.size() == labels.size());
    Preconditions.checkArgument(items.size() == labelProbabilities.size());
  }

  @Override
  public int size() {
    return items.size();
  }

  @Override
  public List<I> getItems() {
    return items;
  }

  @Override
  public List<O> getBestLabels() {
    List<O> bestLabels = Lists.newArrayList();
    for (List<O> labelList : labels) {
      bestLabels.add(labelList.get(0));
    }
    return bestLabels;
  }

  @Override
  public List<List<O>> getLabels() {
    return labels;
  }

  @Override
  public List<List<Double>> getLabelProbabilities() {
    return labelProbabilities;
  }
}
