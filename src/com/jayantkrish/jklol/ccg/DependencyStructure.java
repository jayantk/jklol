package com.jayantkrish.jklol.ccg;

import com.google.common.base.Preconditions;

/**
 * A predicate-argument dependency between two words produced by a CCG
 * parse. A dependency encodes that an object word is an argument to a
 * head word in a particular argument position (which depends on the
 * head's syntactic category).
 * 
 * @author jayantk
 */
public class DependencyStructure {
  private final String head;
  private final int headWordIndex;
  private final HeadedSyntacticCategory headSyntax;

  private final String object;
  private final int objectWordIndex;

  private final int argumentNumber;

  public DependencyStructure(String head, int headWordIndex, HeadedSyntacticCategory headSyntax,
      String object, int objectWordIndex, int argumentNumber) {
    this.head = Preconditions.checkNotNull(head);
    this.headWordIndex = headWordIndex;
    this.headSyntax = Preconditions.checkNotNull(headSyntax);
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

  public HeadedSyntacticCategory getHeadSyntacticCategory() {
    return headSyntax;
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
  public String toString() {
    return "(" + head + ":" + headWordIndex + "," + headSyntax + "," + argumentNumber + ","
        + object + ":" + objectWordIndex + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + argumentNumber;
    result = prime * result + ((head == null) ? 0 : head.hashCode());
    result = prime * result + ((headSyntax == null) ? 0 : headSyntax.hashCode());
    result = prime * result + headWordIndex;
    result = prime * result + ((object == null) ? 0 : object.hashCode());
    result = prime * result + objectWordIndex;
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
    if (argumentNumber != other.argumentNumber)
      return false;
    if (head == null) {
      if (other.head != null)
        return false;
    } else if (!head.equals(other.head))
      return false;
    if (headSyntax == null) {
      if (other.headSyntax != null)
        return false;
    } else if (!headSyntax.equals(other.headSyntax))
      return false;
    if (headWordIndex != other.headWordIndex)
      return false;
    if (object == null) {
      if (other.object != null)
        return false;
    } else if (!object.equals(other.object))
      return false;
    if (objectWordIndex != other.objectWordIndex)
      return false;
    return true;
  }
}