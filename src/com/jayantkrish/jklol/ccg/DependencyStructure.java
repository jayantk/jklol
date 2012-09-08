package com.jayantkrish.jklol.ccg;

public class DependencyStructure {
  private final String head;
  private final int headWordIndex;

  private final String object;
  private final int objectWordIndex;
  
  private final int argumentNumber;
  
  public DependencyStructure(String head, int headWordIndex, String object, 
      int objectWordIndex, int argumentNumber) {
    this.head = head;
    this.headWordIndex = headWordIndex;
    this.object = object;
    this.objectWordIndex = objectWordIndex;
    this.argumentNumber = argumentNumber;
  }
  
  public String getHead() {
    return head;
  }
  
  public int getHeadWordIndex() {
    return headWordIndex;
  }

  public String getObject() {
    return object;
  }
  
  public int getObjectWordIndex() {
    return objectWordIndex;
  }
  
  public int getArgIndex() {
    return argumentNumber;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((head == null) ? 0 : head.hashCode());
    result = prime * result + argumentNumber;
    result = prime * result + ((object == null) ? 0 : object.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DependencyStructure other = (DependencyStructure) obj;
    if (head == null) {
      if (other.head != null)
        return false;
    } else if (!head.equals(other.head))
      return false;
    if (argumentNumber != other.argumentNumber)
      return false;
    if (object == null) {
      if (other.object != null)
        return false;
    } else if (!object.equals(other.object))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "(" + head + ":" + headWordIndex + "," + argumentNumber + "," 
        + object + ":" + objectWordIndex + ")";
  }
}