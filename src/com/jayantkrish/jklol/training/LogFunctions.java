package com.jayantkrish.jklol.training;

public class LogFunctions {

  private static LogFunction log = null;

  public static final void setLogFunction(LogFunction newLog) {
    log = newLog;
  }

  public static final LogFunction getLogFunction() {
    if (log == null) {
      log = new DefaultLogFunction();
    }
    return log;
  }
}