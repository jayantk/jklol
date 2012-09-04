package com.jayantkrish.jklol.ccg;

public class DependencyStructure {
  private final String head;
  private final int headArgIndex;
  private final String object;
  
  public DependencyStructure(String head, int headArgIndex, String object) {
    this.head = head;
    this.headArgIndex = headArgIndex;
    this.object = object;
  }
  
  public String getHead() {
    return head;
  }
  
  public int getArgIndex() {
    return headArgIndex;
  }
  
  public String getObject() {
    return object;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((head == null) ? 0 : head.hashCode());
    result = prime * result + headArgIndex;
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
    if (headArgIndex != other.headArgIndex)
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
    return "(" + head + "," + headArgIndex + "," + object + ")";
  }
}