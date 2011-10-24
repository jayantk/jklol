package com.jayantkrish.jklol.parallel;

/**
 * Stores global mapreduce configuration information for the executing program.
 * Used to retrieve a configured {@code MapReduceExecutor} for running portions
 * of a program.
 * 
 * @author jayantk
 */
public class MapReduceConfiguration {

  private static MapReduceExecutor executor = null;

  /**
   * Sets the global mapreduce executor to the {@code newExecutor}.
   * 
   * @param newExecutor
   */
  public static void setMapReduceExecutor(MapReduceExecutor newExecutor) {
    executor = newExecutor;
  }

  /**
   * Gets the global mapreduce executor.
   * 
   * @return
   */
  public static MapReduceExecutor getMapReduceExecutor() {
    if (executor == null) {
      // Default to using a local executor with one thread per CPU.
      executor = new LocalMapReduceExecutor(
          Runtime.getRuntime().availableProcessors(), 20);
    }
    return executor;
  }

  private MapReduceConfiguration() {
    // Prevent instantiation.
  }
}
