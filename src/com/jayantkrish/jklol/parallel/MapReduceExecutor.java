package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Predicate;

/**
 * {@code MapReduceExecutor} is a simple parallel computing interface
 * for performing embarrassingly parallel tasks. These tasks are
 * formatted as a map (transformation) stage, followed by a reduce
 * (accumulation) stage. Unlike real mapreduce, there is no sort stage
 * between the map and the reduce.
 * 
 * @author jayantk
 */
public interface MapReduceExecutor {

  public <A, B, C, D extends Mapper<A, B>, E extends Reducer<B, C>> C mapReduce(
      Collection<? extends A> items, D mapper, E reducer);

  /**
   * Runs {@code mapper} on each of the given {@code items}.
   * 
   * @param items
   * @param mapper
   * @return
   */
  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper);

  /**
   * Filters {@code items} using predicate. The returned list
   * contains only items for which {@code predicate} returns true.
   * 
   * @param items
   * @param predicate
   * @return
   */
  public <A> List<A> filter(List<A> items, Predicate<A> predicate);
}
