package com.jayantkrish.jklol.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which declares a method as a performance test. Used for running
 * performance tests in the performance/ subdirectory.
 * 
 * @author jayantk
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PerformanceTest {
  // Number of times to run the test and average results over. 
  int value() default 1;
}
