package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;

/**
 * Common implementations of {@link MapReduceExecutor} methods.
 * 
 * @author jayantk
 */
public abstract class AbstractMapReduceExecutor implements MapReduceExecutor {

  @Override
  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper) {
    List<B> results = Lists.newArrayList();
    try {
      List<Future<B>> futureResults = mapAsync(items, mapper);
    
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
    }
    return results;
  }
  
  @Override
  public <A, B, C extends Mapper<A, B>> List<B> map(Collection<? extends A> items, C mapper,
      long timeout, TimeUnit unit) {
    List<B> results = Lists.newArrayList();
    try {
      List<Future<B>> futureResults = mapAsync(items, mapper);
      for (Future<B> future : futureResults) {
        try {
          results.add(future.get(timeout, unit));
        } catch (TimeoutException e) {
          future.cancel(true);
          results.add(null);
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    }
    return results;
  }
}
