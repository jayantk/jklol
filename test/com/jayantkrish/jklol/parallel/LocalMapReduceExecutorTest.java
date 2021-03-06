package com.jayantkrish.jklol.parallel;

import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Predicate;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;

public class LocalMapReduceExecutorTest extends TestCase {

  private LocalMapReduceExecutor executor;
  private List<Double> shortItems;
  private List<Double> longItems;

  @Override
  public void setUp() {
    executor = new LocalMapReduceExecutor(3, 2);
    // Has fewer than the number of batches;
    shortItems = Doubles.asList(new double[] { 0.6, 2.2, 3.3, 3.9 });
    // Has more than the number of batches;
    longItems = Doubles.asList(new double[] { 0.6, 2.2, 3.3, 3.9, 5.1, 6.1, 7.2, 8.3, 9.4 });
  }

  public void testMapReduce() {
    // These tests ensure that the implementation maps and reduces all
    // of the instances in the passed-in collection.
    int value = executor.mapReduce(shortItems, new RoundMapper(), new SumReducer());
    assertEquals(10, value);
    value = executor.mapReduce(longItems, new RoundMapper(), new SumReducer());
    assertEquals(45, value);
  }

  public void testMap() {
    List<Integer> result = executor.map(shortItems, new RoundMapper());
    assertEquals(Ints.asList(1, 2, 3, 4), result);

    result = executor.map(longItems, new RoundMapper());
    assertEquals(Ints.asList(1, 2, 3, 4, 5, 6, 7, 8, 9), result);
  }

  public void testFilter() {
    Predicate<Double> predicate = new Predicate<Double>() {
      @Override
      public boolean apply(Double value) {
        return value > 3; 
      }
    };

    List<Double> result = executor.filter(shortItems, predicate);
    assertEquals(Doubles.asList(3.3, 3.9), result);
    
    result = executor.filter(longItems, predicate);
    assertEquals(Doubles.asList(3.3, 3.9, 5.1, 6.1, 7.2, 8.3, 9.4), result);
  }

  private static class RoundMapper extends Mapper<Double, Integer> {
    @Override
    public Integer map(Double item) {
      return (int) Math.round(item);
    }
  }
 
  private static class SumReducer extends SimpleReducer<Integer> {
    @Override
    public Integer getInitialValue() {
      return 0;
    }

    @Override
    public Integer reduce(Integer item, Integer accumulated) { 
      return item + accumulated;
    }
  }
}
