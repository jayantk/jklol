package com.jayantkrish.jklol.util;

public class TimeUtils {

  public static String durationToString(long durationInMillis) {
    if (durationInMillis < 1000) {
      return durationInMillis + " ms";
    }
    
    long durationInSecs = durationInMillis / 1000;
    if (durationInSecs < 60) {
      return durationInSecs + " s";
    }
    
    long durationInMins = durationInSecs / 60;
    return durationInMins + " mins";
  }

  private TimeUtils() {
    // Prevent instantiation.
  }
}
