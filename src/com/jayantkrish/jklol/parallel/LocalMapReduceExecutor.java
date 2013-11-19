package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.parallel.Reducers.FilterReducer;

/**
 * A parallelized, single-machine implementation of map-reduce pipelines. This
 * executor batches the input items and executes them on multiple local CPUs.
 * 
 * @author jayantk
 */
public class LocalMapReduceExecutor implements MapReduceExecutor {

  private final int batchesPerThread;
  private final int numThreads;

  /**
   * Constructs an executor that processes batches of items using a fixed number
   * of local threads. {@code numThreads} threads are created, and items are
   * batched so that each thread processes at most {@code batchesPerThread}
   * batches.
   *
   * @param numThreads
   * @param batchesPerThread
   */
  public LocalMapReduceExecutor(int numThreads, int batchesPerThread) {
    this.numThreads = numThreads;
    this.batchesPerThread = batchesPerThread;
  }
  
  @Override
  public <A, B, C, D extends Mapper<A, B>, E extends Reducer<B, C>> C mapReduce(
      Collection<? extends A> items, D mapper, E reducer) {
    if (items.size() == 1) {
      // Run all computation in this thread, which is faster given only a small number of items.
      C accumulator = reducer.getInitialValue();
      for (A item : items) {
        B mappedItem = mapper.map(item);
        accumulator = reducer.reduce(mappedItem, accumulator);
      }
      return accumulator;
    }

    ExecutorService executor = getExecutor();
    // Set up the item batches for the executor service. 
    ImmutableList<A> itemsAsList = ImmutableList.copyOf(items);
    int batchSize = (int) Math.ceil(((double) items.size()) / (numThreads * batchesPerThread));
    
    // If batchSize is 1, then there are potentially more batches than items.
    List<Future<C>> results = Lists.newArrayList();
    int numBatches = (int) Math.ceil(((double) items.size()) / batchSize); 
    for (int i = 0; i < numBatches; i++) {
      ImmutableList<A> batchItems = itemsAsList.subList(
          Math.min(i * batchSize, items.size()), Math.min((i + 1) * batchSize, items.size()));
      results.add(executor.submit(new MapReduceBatch<A, B, C>(batchItems, mapper, reducer)));
    }

    // Run the tasks in parallel, aggregating (reducing) their results as 
    // they become available.
    C accumulator = reducer.getInitialValue();
    try {
      for (Future<C> result : results) {
        accumulator = reducer.combine(result.get(), accumulator);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } finally {
      executor.shutdown();
    }
    return accumulator;
  }
  
  @Override
  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper) {
    List<B> results = Lists.newArrayList();
    ExecutorService executor = getExecutor();
    try {
      List<Future<B>> futureResults = Lists.newArrayList();
      for (A item : items) {
        futureResults.add(executor.submit(new MapBatch<A, B>(item, mapper)));
      }

      for (Future<B> future : futureResults) {
        results.add(future.get());
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } finally {
      executor.shutdownNow();
    }
    return results;
  }
  
  @Override
  public <A> List<A> filter(List<A> items, Predicate<A> predicate) {
    return mapReduce(items, Mappers.<A>identity(), new FilterReducer<A>(predicate));
  }

  private ExecutorService getExecutor() {
    // This thread pool executor is equivalent to using 
    // Executors.newFixedThreadPool(numThreads), except that
    // unused threads are eventually terminated, allowing the
    // program to terminate without the user invoking shutdown().
    ThreadPoolExecutor executor = new ThreadPoolExecutor(numThreads, numThreads, 10, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>());
    return executor;
  }

  /**
   * A single batch of items to be processed (mapped and reduced).
   * 
   * @author jayantk
   * @param <A>
   * @param <B>
   */
  private static class MapReduceBatch<A, B, C> implements Callable<C> {

    private final ImmutableList<A> items;
    private final Mapper<A, B> mapper;
    private final Reducer<B, C> reducer;

    public MapReduceBatch(ImmutableList<A> items, Mapper<A, B> mapper, Reducer<B, C> reducer) {
      this.items = items;
      this.mapper = mapper;
      this.reducer = reducer;
    }

    @Override
    public C call() {
      C accumulator = reducer.getInitialValue();
      for (A item : items) {
        B mappedItem = mapper.map(item);
        accumulator = reducer.reduce(mappedItem, accumulator);
      }
      return accumulator;
    }
  }
  
  public static class MapBatch<A, B> implements Callable<B> {
    private final A item;
    private final Mapper<A, B> mapper;

    public MapBatch(A item, Mapper<A, B> mapper) {
      this.item = item;
      this.mapper = mapper;
    }
    
    @Override
    public B call() {
      return mapper.map(item);
    }
  }
}
