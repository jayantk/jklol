package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;

/**
 * A CCG category composed of both a syntactic and semantic type. 
 * Syntax is represented using a {@code SyntacticCategory}, while 
 * the semantics are contained within this class itself.
 * <p>
 * CCG semantics are represented in the dependency format. Each
 * category contains a number of unfilled dependencies, represented
 * as a (subject, argument number, object) tuples. Both subject and
 * object may be variables that represent yet-unfilled arguments to
 * this category. Such variables are the typically case for objects,
 * and may be used for subjects to project long-range dependencies.
 * <p>
 * Each CCG category also has zero or more semantic heads. When this
 * category is consumed by another category, these heads are used to 
 * fill the object dependencies in the consuming category.
 * 
 * @author jayant
 */
public class CcgCategory implements Serializable {
  private static final long serialVersionUID = 1L;
  
  // NOTE: Remember to change .equals() and .hashCode() if these members 
  // are modified.
  
  // The syntactic type of this category
  private final SyntacticCategory syntax;
  // The category's semantic heads.
  private final Set<Argument> heads;
  
  // The semantic dependencies of this category, both filled and unfilled.
  private final List<Argument> subjects;
  private final List<Argument> objects;
  private final List<Integer> argumentNumbers;
  
  private static final char ENTRY_DELIMITER=',';
  private static final char DEPENDENCY_DELIMITER='#';
  
  public CcgCategory(SyntacticCategory syntax, Set<Argument> heads,  
      List<Argument> subjects, List<Argument> objects, List<Integer> argumentNumbers) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.heads = Preconditions.checkNotNull(heads);
    
    this.subjects = ImmutableList.copyOf(subjects);
    this.objects = ImmutableList.copyOf(objects);
    this.argumentNumbers = ImmutableList.copyOf(argumentNumbers);
  }

  /**
   * Parses a CCG category from a string. The format is:
   * <p>
   * (#-separated head word list),(syntactic type),(#-separated dependency list).
   * <p>
   * For example, the category for "in" is: "in,(N\N)/N,in 1 ?1, in 2 ?2".
   *  
   * @param categoryString
   * @return
   */
  public static CcgCategory parseFrom(String categoryString) {
    try {
      String[] parts = new CSVParser(ENTRY_DELIMITER).parseLine(categoryString);
      Preconditions.checkArgument(parts.length >= 2, "Invalid CCG category string: %s", 
          categoryString);
      if (parts.length > 2) {
        return parseFrom(parts[0], parts[1], parts[2]);
      } else {
        return parseFrom(parts[0], parts[1], "");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid CCG category: " + categoryString, e);
    }
  }

  public static CcgCategory parseFrom(String semanticHeadString, String syntaxString, String dependencyString) {
    try {
      CSVParser dependencyParser = new CSVParser(DEPENDENCY_DELIMITER);
      List<String> headStrings = Lists.newArrayList(dependencyParser.parseLine(semanticHeadString));
      Set<Argument> heads = Sets.newHashSet(); 
      for (String headString : headStrings) {
        heads.add(Argument.parseFromString(headString));
      }

      SyntacticCategory syntax = SyntacticCategory.parseFrom(syntaxString);

      // Parse the semantic dependencies.
      List<Argument> subjects = Lists.newArrayList();
      List<Argument> objects = Lists.newArrayList();
      List<Integer> argumentNumbers = Lists.newArrayList();
      if (dependencyString.trim().length() > 0) {
        String[] depStrings = dependencyParser.parseLine(dependencyString);
        for (int i = 0; i < depStrings.length; i++) {
          String[] depParts = depStrings[i].split(" ");
          Preconditions.checkArgument(depParts.length == 3, "Invalid CCG semantic dependency: %s", 
              depStrings[i]);

          argumentNumbers.add(Integer.parseInt(depParts[1]));
          subjects.add(Argument.parseFromString(depParts[0]));
          objects.add(Argument.parseFromString(depParts[2]));
        }
      }

      return new CcgCategory(syntax, heads, subjects, objects, argumentNumbers);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid CCG category: " + semanticHeadString + "," 
          + syntaxString + "," + dependencyString, e);
    }
  }

  /**
   * Gets the syntactic type of this category.
   *  
   * @return
   */
  public SyntacticCategory getSyntax() {
    return syntax;
  }
  
  /**
   * Gets the head semantic type of this category.
   * 
   * @return
   */
  public Set<Argument> getHeads() {
    return heads;
  }
  
  public List<Argument> getSubjects() {
    return subjects;
  }
  
  public List<Argument> getObjects() {
    return subjects;
  }
  
  public List<Integer> getArgumentNumbers() {
    return argumentNumbers;
  }
  
  public Multimap<Integer, UnfilledDependency> createUnfilledDependencies(int wordIndex,
      List<DependencyStructure> appendDependencies) {
    Multimap<Integer, UnfilledDependency> map = HashMultimap.create();
    for (int i = 0; i < subjects.size(); i++) {
      Set<IndexedPredicate> subject = null;
      int subjectIndex = -1;
      if (subjects.get(i).hasPredicate()) {
        subject = Sets.newHashSet(new IndexedPredicate(subjects.get(i).getPredicate(), wordIndex));
      } else {
        subjectIndex = subjects.get(i).getArgumentNumber();
      }
      
      Set<IndexedPredicate> object = null;
      int objectIndex = -1;
      if (objects.get(i).hasPredicate()) {
        object = Sets.newHashSet(new IndexedPredicate(objects.get(i).getPredicate(), wordIndex));
      } else {
        objectIndex = objects.get(i).getArgumentNumber();
      }

      UnfilledDependency dep = new UnfilledDependency(subject, subjectIndex, 
          argumentNumbers.get(i), object, objectIndex);
      if (!dep.hasSubjects()) {
        map.put(dep.getSubjectIndex(), dep);
      }
      if (!dep.hasObjects()) {
        map.put(dep.getObjectIndex(), dep);
      }
      if (dep.hasSubjects() && dep.hasObjects()) {
        for (IndexedPredicate subj : dep.getSubjects()) {
          for (IndexedPredicate obj : dep.getObjects()) {
            appendDependencies.add(new DependencyStructure(subj.getHead(), subj.getHeadIndex(),
                obj.getHead(), obj.getHeadIndex(), argumentNumbers.get(i)));
          }
        }
      }
    }
    return map;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentNumbers == null) ? 0 : argumentNumbers.hashCode());
    result = prime * result + ((heads == null) ? 0 : heads.hashCode());
    result = prime * result + ((objects == null) ? 0 : objects.hashCode());
    result = prime * result + ((subjects == null) ? 0 : subjects.hashCode());
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
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
    if (argumentNumbers == null) {
      if (other.argumentNumbers != null)
        return false;
    } else if (!argumentNumbers.equals(other.argumentNumbers))
      return false;
    if (heads == null) {
      if (other.heads != null)
        return false;
    } else if (!heads.equals(other.heads))
      return false;
    if (objects == null) {
      if (other.objects != null)
        return false;
    } else if (!objects.equals(other.objects))
      return false;
    if (subjects == null) {
      if (other.subjects != null)
        return false;
    } else if (!subjects.equals(other.subjects))
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return heads.toString() + ":" + syntax.toString();
  }
  
  public static final class Argument implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int argumentNumber;
    private final String predicate;

    private Argument(int argumentNumber, String predicate) {
      this.argumentNumber = argumentNumber;
      this.predicate = predicate;
    }
    
    public static Argument parseFromString(String argString) {
      if (!argString.startsWith("?")) {
        return Argument.createFromPredicate(argString);
      } else {
        Integer argInd = Integer.parseInt(argString.substring(1));
        return Argument.createFromArgumentNumber(argInd);
      }
    }
    
    public static Argument createFromPredicate(String predicate) {
      return new Argument(-1, predicate);
    }
    
    public static Argument createFromArgumentNumber(int argumentNumber) {
      return new Argument(argumentNumber, null);
    }
    
    public boolean hasPredicate() {
      return predicate != null;
    }
    
    public int getArgumentNumber() {
      return argumentNumber;
    }
    
    public String getPredicate() {
      return predicate;
    }
    
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + argumentNumber;
      result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
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
      Argument other = (Argument) obj;
      if (argumentNumber != other.argumentNumber)
        return false;
      if (predicate == null) {
        if (other.predicate != null)
          return false;
      } else if (!predicate.equals(other.predicate))
        return false;
      return true;
    }
    
    @Override
    public String toString() {
      return (predicate != null) ? predicate : ("?" + argumentNumber); 
    }
  }
}