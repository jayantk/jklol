package com.jayantkrish.jklol.training;

public class LogFunctions {

  private static LogFunction log = null;

  public static void setLogFunction(LogFunction newLog) {
    log = newLog;
  }

  public static LogFunction getLogFunction() {
    if (log == null) {
      log = new DefaultLogFunction();
    }
    return log;
  }
}