package com.jayantkrish.jklol.parallel;

import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;

public class LocalMapReduceExecutorTest extends TestCase {

  private LocalMapReduceExecutor executor;
  private List<Double> shortItems;
  private List<Double> longItems;
  private List<Double> mapItems;

  @Override
  public void setUp() {
    executor = new LocalMapReduceExecutor(3, 2);
    // Has fewer than the number of batches;
    shortItems = Doubles.asList(new double[] { 0.6, 2.2, 3.3, 3.9 });
    // Has more than the number of batches;
    longItems = Doubles.asList(new double[] { 0.6, 2.2, 3.3, 3.9, 5.1, 6.1, 7.2, 8.3, 9.4 });
    
    mapItems = Doubles.asList(new double[] { 0.6, 0.0, 3.3 });
  }

  public void testMapReduce() {
    // These tests ensure that the implementation maps and reduces all
    // of the instances in the passed-in collection.
    int value = executor.mapReduce(shortItems, new RoundMapper(), new SumReducer());
    assertEquals(10, value);
    value = executor.mapReduce(longItems, new RoundMapper(), new SumReducer());
    assertEquals(45, value);
  }
  

  public void testMapCancel() throws InterruptedException {
    final List<Integer> values = Lists.newArrayList();;
    Thread t = new Thread() {  
      public void run() {  
        values.addAll(executor.map(mapItems, new InfiniteMapper(), 100, TimeUnit.MILLISECONDS));
      }  
    };  
    t.start();  
    t.join(2000); 

    assertTrue("Execution did not complete in 2 seconds.", values.size() == 3);

    List<Integer> expected = Lists.newArrayList();
    expected.add(1);
    expected.add(null);
    expected.add(3);
    assertEquals(values, expected);
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
  
  private static class InfiniteMapper extends Mapper<Double, Integer> {
    @Override
    public Integer map(Double item) {
      if (item == 0.0) {
        // Loop forever to check job cancellation.
        while (true) {}
      } else {
        return (int) Math.round(item);
      }
    }
  }
}
