package com.jayantkrish.jklol.sequence;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Default implementation of a tagged sequence.
 * 
 * @author jayantk
 * @param <I>
 * @param <O>
 */
public class ListTaggedSequence<I, O> implements TaggedSequence<I, O> {
  
  private final List<I> items;
  private final List<O> labels;

  public ListTaggedSequence(List<I> items, List<O> labels) {
    this.items = Preconditions.checkNotNull(items);
    this.labels = labels;

    Preconditions.checkArgument(labels == null || items.size() == labels.size());
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
  public List<O> getLabels() {
    return labels;
  }

  @Override
  public List<LocalContext<I>> getLocalContexts() {
    List<LocalContext<I>> contexts = Lists.newArrayList();
    for (int i = 0; i < items.size(); i++) {
      contexts.add(new ListLocalContext<I>(items, i));
    }
    return contexts;
  }
}
