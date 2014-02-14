package com.jayantkrish.jklol.lisp;

public abstract class AbstractParameterSpec implements ParameterSpec {

  private final int id;
  
  private static int idCounter = 0;
  
  public AbstractParameterSpec(int id) {
    this.id = id;
  }

  @Override
  public int getId() {
    return id;
  }

  public static int getUniqueId() {
    return idCounter++;
  }
}
