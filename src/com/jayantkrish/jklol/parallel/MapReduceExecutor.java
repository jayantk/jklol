package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper);

  /**
   * Runs {@code mapper} on each of the given {@code items}, while
   * upper bounding the maximum running time of mapper for each item.
   * If {@code timeout} is exceeded for an item, then the item is
   * mapped to {@code null}.
   * 
   * @param items
   * @param mapper
   * @param timeout
   * @param unit
   * @return
   */
  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper,
      long timeout, TimeUnit unit);

  public <A, B, C extends Mapper<A, B>> List<Future<B>> mapAsync(Collection<? extends A> items, C mapper)
      throws InterruptedException;
}
