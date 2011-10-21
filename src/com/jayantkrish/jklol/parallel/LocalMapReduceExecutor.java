package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * An parallelized, single-machine implementation of map-reduce pipelines. This
 * executor batches the input objects and executes them on multiple local CPUs.
 * 
 * @author jayantk
 */
public class LocalMapReduceExecutor implements MapReduceExecutor {

  private final int batchesPerThread;
  private final int numThreads;
  private final ExecutorService executor;

  public LocalMapReduceExecutor(int numThreads, int batchesPerThread) {
    this.numThreads = numThreads;
    this.batchesPerThread = batchesPerThread;
    this.executor = Executors.newFixedThreadPool(numThreads);
  }

  @Override
  public <A, B> B mapReduce(Collection<A> items,
      Mapper<A, B> mapper, Reducer<B> reducer) {

    // Set up the item batches for the executor service. 
    ImmutableList<A> itemsAsList = ImmutableList.copyOf(items);
    List<MapReduceBatch<A, B>> batches = Lists.newArrayList();
    int batchSize = (int) Math.ceil(((double) items.size()) / (numThreads * batchesPerThread));
    for (int i = 0; i < numThreads * batchesPerThread; i++) {
      ImmutableList<A> batchItems = itemsAsList.subList(
          Math.min(i * batchSize, items.size()), Math.min((i + 1) * batchSize, items.size()));
      batches.add(new MapReduceBatch<A, B>(batchItems, mapper, reducer));
    }
    
    // Run the tasks in parallel, aggregating (reducing) their results as 
    // they become available.
    B accumulator = reducer.getInitialValue();
    try {
      List<Future<B>> results = executor.invokeAll(batches);
      for (Future<B> result : results) {
        accumulator = reducer.reduce(result.get(), accumulator);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return accumulator;
  }

  /**
   * A single batch of items to be processed (mapped and reduced).
   * 
   * @author jayantk
   * @param <A>
   * @param <B>
   */
  private static class MapReduceBatch<A, B> implements Callable<B> {

    private final ImmutableList<A> items;
    private final Mapper<A, B> mapper;
    private final Reducer<B> reducer;

    public MapReduceBatch(ImmutableList<A> items, Mapper<A, B> mapper, Reducer<B> reducer) {
      this.items = items;
      this.mapper = mapper;
      this.reducer = reducer;
    }

    @Override
    public B call() {
      B accumulator = reducer.getInitialValue();
      for (A item : items) {
        B mappedItem = mapper.map(item);
        accumulator = reducer.reduce(mappedItem, accumulator);
      }
      return accumulator;
    }
  }
}
