package com.jayantkrish.jklol.ccg;

import java.io.Serializable;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;

public class UnfilledDependency implements Serializable {
  private static final long serialVersionUID = 1L;

  // Subject is the word projecting the dependency. Null if the subject is unfilled. 
  private final IndexedPredicate subject;
  // Subject may be unfilled. If so, then this variable 
  // is the index of the argument which fills the subject role. 
  private final int subjectFunctionVarIndex;
  // If subject is a variable, then it is a function. This index
  // tracks which argument of the subject function is filled by the object role.
  // (i.e., which dependencies inherited from the subject are filled by the object.)
  private final int subjectArgIndex;

  // Object is the arguments of the projected dependency. Null if object is unfilled. 
  private final IndexedPredicate object;
  private final int objectArgumentIndex;

  public UnfilledDependency(IndexedPredicate subject, int subjectFunctionVarIndex, 
      int subjectArgIndex, IndexedPredicate object, int objectIndex) {
    this.subject = subject;
    this.subjectFunctionVarIndex = subjectFunctionVarIndex;
    this.subjectArgIndex = subjectArgIndex;
    this.object = object;
    this.objectArgumentIndex = objectIndex;
  }

  public static UnfilledDependency createWithKnownSubject(String subject, int subjectHeadWordIndex, 
      int subjectArgIndex, int objectIndex) {
    return new UnfilledDependency(new IndexedPredicate(subject, subjectHeadWordIndex),
        -1, subjectArgIndex, null, objectIndex);
  }

  public static UnfilledDependency createWithFunctionSubject(int subjectIndex, int subjectArgIndex, 
      int objectIndex) {
    return new UnfilledDependency(null, subjectIndex, subjectArgIndex, null, objectIndex);
  }

  public static UnfilledDependency createWithKnownObject(int subjectIndex, int subjectArgIndex, 
      String object, int objectHeadWordIndex) {
    return new UnfilledDependency(null, subjectIndex, subjectArgIndex, 
        new IndexedPredicate(object, objectHeadWordIndex), -1);
  }

  public IndexedPredicate getSubject() {
    return subject;
  }

  public boolean hasSubject() {
    return subject != null;
  }

  public int getSubjectIndex() {
    return subjectFunctionVarIndex;
  }

  public IndexedPredicate getObject() {
    return object;
  }

  public boolean hasObject() {
    return object != null;
  }
  
  /**
   * Returns {@code true} if this dependency has both a subject and
   * an object.
   *  
   * @return
   */
  public boolean isFilledDependency() {
    return subject != null && object != null;
  }
  
  public DependencyStructure toDependencyStructure() {
    Preconditions.checkState(isFilledDependency());
    return new DependencyStructure(subject.getHead(), subject.getHeadIndex(), 
        object.getHead(), object.getHeadIndex(), subjectArgIndex);
  }

  public int getObjectIndex() {
    return objectArgumentIndex;
  }

  public int getArgumentIndex() {
    return subjectArgIndex;
  }
      
  /**
   * Returns a new UnfilledDependency whose subject is {@code newSubjectIndex} 
   * and whose object is equal to {@code this}'s object.
   *  
   * @param newSubjectIndex
   * @return
   */
  public UnfilledDependency replaceSubject(int newSubjectIndex) {
    return new UnfilledDependency(null, newSubjectIndex, subjectArgIndex, object, objectArgumentIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose subject is equal to 
   * {@code newSubject} and whose object is equal to {@code this}' object.
   *  
   * @param newSubject
   * @return
   */
  public UnfilledDependency replaceSubject(IndexedPredicate newSubject) {
    return new UnfilledDependency(newSubject, -1, subjectArgIndex, object, objectArgumentIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose object is {@code newObjectIndex} 
   * and whose subject is equal to {@code this}'s subject.
   *  
   * @param newObjectIndex
   * @return
   */
  public UnfilledDependency replaceObject(int newObjectIndex) {
    return new UnfilledDependency(subject, subjectFunctionVarIndex, subjectArgIndex, null, newObjectIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose object is {@code newObject} 
   * and whose subject is equal to {@code this}'s subject.
   *  
   * @param newObject
   * @return
   */
  public UnfilledDependency replaceObject(IndexedPredicate newObject) {
    return new UnfilledDependency(subject, subjectFunctionVarIndex, subjectArgIndex, newObject, -1);
  }
  
  @Override
  public String toString() {
    return subjectFunctionVarIndex + " " + subjectArgIndex + " " + objectArgumentIndex;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + objectArgumentIndex;
    result = prime * result + ((object == null) ? 0 : object.hashCode());
    result = prime * result + subjectArgIndex;
    result = prime * result + subjectFunctionVarIndex;
    result = prime * result + ((subject == null) ? 0 : subject.hashCode());
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
    UnfilledDependency other = (UnfilledDependency) obj;
    if (objectArgumentIndex != other.objectArgumentIndex)
      return false;
    if (object == null) {
      if (other.object != null)
        return false;
    } else if (!object.equals(other.object))
      return false;
    if (subjectArgIndex != other.subjectArgIndex)
      return false;
    if (subjectFunctionVarIndex != other.subjectFunctionVarIndex)
      return false;
    if (subject == null) {
      if (other.subject != null)
        return false;
    } else if (!subject.equals(other.subject))
      return false;
    return true;
  }
}
