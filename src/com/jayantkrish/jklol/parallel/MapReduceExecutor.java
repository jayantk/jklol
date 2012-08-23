package com.jayantkrish.jklol.parallel;

import java.util.Collection;

/**
 * {@code MapReduceExecutor} is a simple parallel computing interface for
 * performing embarrassingly parallel tasks. These tasks are formatted as a map
 * (transformation) stage, followed by a reduce (accumulation) stage. Unlike
 * real mapreduce, there is no sort stage between the map and the reduce.
 * 
 * @author jayantk
 */
public interface MapReduceExecutor {

  public <A, B, C> C mapReduce(Collection<A> items, Mapper<A, B> mapper, Reducer<B, C> reducer);
}
