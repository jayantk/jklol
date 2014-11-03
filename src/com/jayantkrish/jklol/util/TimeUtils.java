package com.jayantkrish.jklol.util;

/**
 * Utilities for manipulating times and durations.
 * 
 * @author jayant
 *
 */
public class TimeUtils {

  public static String durationToString(long durationInMillisLong) {
    double durationInMillis = (double) durationInMillisLong;
    return durationToString(durationInMillis);
  }

  public static String durationToString(double durationInMillis) {
    if (durationInMillis < 1000) {
      return String.format("%.3f ms", durationInMillis);
    }

    double durationInSecs = durationInMillis / 1000;
    if (durationInSecs < 60) {
      return String.format("%.3f sec", durationInSecs);
    }

    double durationInMins = durationInSecs / 60;
    if (durationInMins < 60) {
      return String.format("%.3f mins", durationInMins);
    }

    double durationInHours = durationInMins / 60;
    return String.format("%.3f hours", durationInHours);
  }

  private TimeUtils() {
    // Prevent instantiation.
  }
}
