package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;

public class UnfilledDependency implements Serializable {
  private static final long serialVersionUID = 1L;

  // Subject is the word(s) projecting the dependency. Null if subjects is unfilled. 
  private final Set<IndexedPredicate> subjects;
  // Subject may be unfilled. If so, then this variable 
  // is the index of the argument which fills the subject role. 
  private final int subjectFunctionVarIndex;
  // If subject is a variable, then it is a function. This index
  // tracks which argument of the subject function is filled by the object role.
  // (i.e., which dependencies inherited from the subject are filled by the object.)
  private final int subjectArgIndex;

  // Objects are the arguments of the projected dependency. Null if objects is unfilled. 
  private final Set<IndexedPredicate> objects;
  private final int objectArgumentIndex;

  public UnfilledDependency(Set<IndexedPredicate> subjects, int subjectFunctionVarIndex, 
      int subjectArgIndex, Set<IndexedPredicate> objects, int objectIndex) {
    this.subjects = subjects;
    this.subjectFunctionVarIndex = subjectFunctionVarIndex;
    this.subjectArgIndex = subjectArgIndex;
    this.objects = objects;
    this.objectArgumentIndex = objectIndex;
  }

  public static UnfilledDependency createWithKnownSubject(String subject, int subjectHeadWordIndex, 
      int subjectArgIndex, int objectIndex) {
    return new UnfilledDependency(Sets.newHashSet(new IndexedPredicate(subject, subjectHeadWordIndex)),
        -1, subjectArgIndex, null, objectIndex);
  }

  public static UnfilledDependency createWithFunctionSubject(int subjectIndex, int subjectArgIndex, 
      int objectIndex) {
    return new UnfilledDependency(null, subjectIndex, subjectArgIndex, null, objectIndex);
  }

  public static UnfilledDependency createWithKnownObject(int subjectIndex, int subjectArgIndex, 
      String object, int objectHeadWordIndex) {
    return new UnfilledDependency(null, subjectIndex, subjectArgIndex, 
        Sets.newHashSet(new IndexedPredicate(object, objectHeadWordIndex)), -1);
  }

  public Set<IndexedPredicate> getSubjects() {
    return subjects;
  }

  public boolean hasSubjects() {
    return subjects != null;
  }

  public int getSubjectIndex() {
    return subjectFunctionVarIndex;
  }

  public Set<IndexedPredicate> getObjects() {
    return objects;
  }

  public boolean hasObjects() {
    return objects != null;
  }

  public int getObjectIndex() {
    return objectArgumentIndex;
  }

  public int getArgumentIndex() {
    return subjectArgIndex;
  }
      
  /**
   * Returns a new UnfilledDependency whose subject is {@code newSubjectIndex} 
   * and whose objects are equal to {@code this}'s objects.
   *  
   * @param newSubjectIndex
   * @return
   */
  public UnfilledDependency replaceSubject(int newSubjectIndex) {
    return new UnfilledDependency(null, newSubjectIndex, subjectArgIndex, objects, objectArgumentIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose subjects are {@code newSubjects} 
   * and whose objects are equal to {@code this}'s objects.
   *  
   * @param newSubjects
   * @return
   */
  public UnfilledDependency replaceSubject(Set<IndexedPredicate> newSubjects) {
    return new UnfilledDependency(newSubjects, -1, subjectArgIndex, objects, objectArgumentIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose object is {@code newObjectIndex} 
   * and whose subjects are equal to {@code this}'s subjects.
   *  
   * @param newObjectIndex
   * @return
   */
  public UnfilledDependency replaceObject(int newObjectIndex) {
    return new UnfilledDependency(subjects, subjectFunctionVarIndex, subjectArgIndex, null, newObjectIndex);
  }
  
  /**
   * Returns a new UnfilledDependency whose objects are {@code newObjects} 
   * and whose subjects are equal to {@code this}'s subjects.
   *  
   * @param newObjects
   * @return
   */
  public UnfilledDependency replaceObject(Set<IndexedPredicate> objects) {
    return new UnfilledDependency(subjects, subjectFunctionVarIndex, subjectArgIndex, objects, -1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + objectArgumentIndex;
    result = prime * result + ((objects == null) ? 0 : objects.hashCode());
    result = prime * result + subjectArgIndex;
    result = prime * result + subjectFunctionVarIndex;
    result = prime * result + ((subjects == null) ? 0 : subjects.hashCode());
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
    if (objects == null) {
      if (other.objects != null)
        return false;
    } else if (!objects.equals(other.objects))
      return false;
    if (subjectArgIndex != other.subjectArgIndex)
      return false;
    if (subjectFunctionVarIndex != other.subjectFunctionVarIndex)
      return false;
    if (subjects == null) {
      if (other.subjects != null)
        return false;
    } else if (!subjects.equals(other.subjects))
      return false;
    return true;
  }
}
