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
    if (durationInMillis < 1000) {
      return durationInMillis + " ms";
    }

    double durationInSecs = durationInMillis / 1000;
    if (durationInSecs < 60) {
      return durationInSecs + " s";
    }

    double durationInMins = durationInSecs / 60;
    if (durationInMins < 60) {
      return durationInMins + " mins";
    }

    double durationInHours = durationInMins / 60;
    return durationInHours + " hours";
  }

  private TimeUtils() {
    // Prevent instantiation.
  }
}
