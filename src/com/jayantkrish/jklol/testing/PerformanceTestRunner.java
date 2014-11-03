package com.jayantkrish.jklol.testing;

import java.lang.reflect.Method;

/**
 * Program for running performance tests, which are methods annotated using
 * {@link PerformanceTest}.
 * 
 * @author jayantk
 */
public class PerformanceTestRunner {

  public static void run(PerformanceTestCase testCase) {
    Class<? extends PerformanceTestCase> testCaseClass = testCase.getClass();
    Method[] methods = testCaseClass.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PerformanceTest test = methods[i].getAnnotation(PerformanceTest.class);
      if (test != null) {
        runTest(testCase, methods[i], test.value());
      }
    }
  }
  
  public static void runTest(PerformanceTestCase testCase, Method testMethod, int repetitions) {
    long total = 0; 
    try {
      for (int j = 0; j < repetitions; j++) {
        testCase.setUp();
        long start = System.nanoTime();
        testMethod.invoke(testCase);
        total += System.nanoTime() - start;
        testCase.tearDown();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    double avgTime = ((double) total) / (repetitions * 1000000);
    System.out.println(testMethod.getName() + ": " + avgTime + " ms");
  }
}
