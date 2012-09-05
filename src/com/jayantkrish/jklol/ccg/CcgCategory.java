package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;

public class CcgCategory implements Serializable {
  private static final long serialVersionUID = 1L;
  
  // NOTE: Remember to change .equals() and .hashCode() if these members 
  // are modified.
  private final SyntacticCategory syntax;
  private final Set<String> heads;
  
  private final Multimap<Integer, UnfilledDependency> unfilledDependencies;
  
  public CcgCategory(SyntacticCategory syntax, Set<String> heads,  
      Multimap<Integer, UnfilledDependency> unfilledDependencies) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.heads = Preconditions.checkNotNull(heads);
    
    this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);
  }
  
  public static CcgCategory parseFrom(String categoryString) {
    String[] parts = categoryString.split(",");
    Set<String> heads = Sets.newHashSet(parts[0].split("#"));
    
    SyntacticCategory syntax = SyntacticCategory.parseFrom(parts[1]);

    // Parse the semantic dependencies.
    Multimap<Integer, UnfilledDependency> dependencies = HashMultimap.create();
    if (parts.length > 2) {
      String[] depStrings = parts[2].split("#");
      for (int i = 0; i < depStrings.length; i++) {
        String[] depParts = depStrings[i].split(" ");

        Integer argInd = Integer.parseInt(depParts[1]);
        Integer objectInd = Integer.parseInt(depParts[2].substring(1));

        UnfilledDependency dep;
        if (!depParts[0].startsWith("?")) {
          dep = UnfilledDependency.createWithKnownSubject(depParts[0], argInd, objectInd);
          dependencies.put(objectInd, dep);
        } else {
          Integer subjectInd = Integer.parseInt(depParts[0].substring(1));
          dep = UnfilledDependency.createWithFunctionSubject(subjectInd, argInd, objectInd);
          dependencies.put(objectInd, dep);
          dependencies.put(subjectInd, dep);
        }
      }
    }
    
    return new CcgCategory(syntax, heads, dependencies);
  }
 
  public Set<String> getHeads() {
    return heads;
  }
  
  public SyntacticCategory getSyntax() {
    return syntax;
  }

  public CcgCombinationResult apply(CcgCategory other, Direction direction) {
    if (syntax.isAtomic() || !syntax.acceptsArgumentOn(direction) || 
        !syntax.getArgument().isUnifiableWith(other.getSyntax())) {
      return null;
    }
    
    Set<String> newHeads = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) ? other.getHeads() : heads;
    
    // Resolve semantic dependencies. Fill all dependency slots which require this argument.
    // Return any fully-filled dependencies, while saving partially-filled dependencies for later.
    int argNum = syntax.getArgumentList().size(); 
    Multimap<Integer, UnfilledDependency> newDeps = HashMultimap.create(unfilledDependencies);
    newDeps.removeAll(argNum);
    
    List<DependencyStructure> filledDeps = Lists.newArrayList();
    for (UnfilledDependency unfilled : unfilledDependencies.get(argNum)) {
      
      if (unfilled.getObjectIndex() == argNum) {
        Set<String> objects = other.getHeads();
        if (unfilled.hasSubjects()) { 
          for (String head : unfilled.getSubjects()) {
            for (String object : objects) {
              filledDeps.add(new DependencyStructure(head, unfilled.getArgumentIndex(), object));
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int subjectIndex = unfilled.getSubjectIndex();
          int argIndex = unfilled.getArgumentIndex();
          
          newDeps.remove(subjectIndex, unfilled);
          for (String object : objects) {
            newDeps.put(subjectIndex, UnfilledDependency.createWithKnownObject(subjectIndex, argIndex, object));
          }
        }
      } else if (unfilled.getSubjectIndex() == argNum) {
        Collection<UnfilledDependency> inheritedDeps = other.unfilledDependencies.get(unfilled.getArgumentIndex());
        if (unfilled.hasObjects()) { 
          for (String object : unfilled.getObjects()) {
            for (UnfilledDependency inheritedDep : inheritedDeps) {
              for (String subject : inheritedDep.getSubjects()) {
                filledDeps.add(new DependencyStructure(subject, inheritedDep.getArgumentIndex(), object));
              }
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int objectIndex = unfilled.getObjectIndex();
          newDeps.remove(objectIndex, unfilled);
          
          for (UnfilledDependency inheritedDep : inheritedDeps) {
            newDeps.put(objectIndex, new UnfilledDependency(inheritedDep.getSubjects(), inheritedDep.getSubjectIndex(), 
                inheritedDep.getArgumentIndex(), null, objectIndex)); 
          }
        }
      }
    }
    
    CcgCategory category = new CcgCategory(syntax.getReturn(), newHeads, newDeps); 
    
    return new CcgCombinationResult(category, filledDeps);
  }

  public CcgCombinationResult compose(CcgCategory other, Direction direction) {
    return null;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((heads == null) ? 0 : heads.hashCode());
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
    result = prime * result + ((unfilledDependencies == null) ? 0 : unfilledDependencies.hashCode());
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
    CcgCategory other = (CcgCategory) obj;
    if (heads == null) {
      if (other.heads != null)
        return false;
    } else if (!heads.equals(other.heads))
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    if (unfilledDependencies == null) {
      if (other.unfilledDependencies != null)
        return false;
    } else if (!unfilledDependencies.equals(other.unfilledDependencies))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return heads.toString() + " : " + syntax.toString();
  }

  public static class CcgCombinationResult {
    private CcgCategory category;
    private List<DependencyStructure> dependencies;

    public CcgCombinationResult(CcgCategory category, List<DependencyStructure> dependencies) {
      this.category = Preconditions.checkNotNull(category);
      this.dependencies = Preconditions.checkNotNull(dependencies);
    }

    public CcgCategory getCategory() {
      return category;
    }

    public List<DependencyStructure> getDependencies() {
      return dependencies;
    }
  }

  public static class UnfilledDependency implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Subject is the word(s) projecting the dependency. Null if subjects is unfilled. 
    private final Set<String> subjects;
    // Subject may be unfilled. If so, then this variable 
    // is the index of the argument which fills the subject role. 
    private final int subjectFunctionVarIndex;
    // If subject is a variable, then it is a function. This index
    // tracks which argument of the subject function is filled by the object role.
    // (i.e., which dependencies inherited from the subject are filled by the object.)
    private final int subjectArgIndex;
    
    // Objects are the arguments of the projected dependency. Null if objects is unfilled. 
    private final Set<String> objects;
    private final int objectArgumentIndex;

    public UnfilledDependency(Set<String> subjects, int subjectFunctionVarIndex, 
        int subjectArgIndex, Set<String> objects, int objectIndex) {
      this.subjects = subjects;
      this.subjectFunctionVarIndex = subjectFunctionVarIndex;
      this.subjectArgIndex = subjectArgIndex;
      this.objects = objects;
      this.objectArgumentIndex = objectIndex;
    }
    
    public static UnfilledDependency createWithKnownSubject(String subject, int subjectArgIndex, int objectIndex) {
      return new UnfilledDependency(Sets.newHashSet(subject), -1, subjectArgIndex, null, objectIndex);
    }
    
    public static UnfilledDependency createWithFunctionSubject(int subjectIndex, int subjectArgIndex, 
        int objectIndex) {
      return new UnfilledDependency(null, subjectIndex, subjectArgIndex, null, objectIndex);
    }
    
    public static UnfilledDependency createWithKnownObject(int subjectIndex, int subjectArgIndex, String object) {
      return new UnfilledDependency(null, subjectIndex, subjectArgIndex, Sets.newHashSet(object), -1);
    }
    
    public Set<String> getSubjects() {
      return subjects;
    }
    
    public boolean hasSubjects() {
      return subjects != null;
    }
    
    public int getSubjectIndex() {
      return subjectFunctionVarIndex;
    }
    
    public Set<String> getObjects() {
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
}