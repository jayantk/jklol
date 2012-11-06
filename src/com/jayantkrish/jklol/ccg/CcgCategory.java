package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;

/**
 * A CCG category composed of both a syntactic and semantic type.
 * Syntax is represented using a {@code HeadedSyntacticCategory},
 * while the semantics are contained within this class itself.
 * <p>
 * CCG semantics are represented in the dependency format and made up
 * of two parts: (1) a set of unfilled dependencies, and (2) a set of
 * semantic variable assignments. Each category contains a number of
 * unfilled dependencies, represented as a (subject, argument number,
 * object) tuples. The subject is the name of a predicate, the
 * argument number an argument to that predicate, and the object a
 * semantic variable. The semantic variable assignment is a mapping
 * from semantic variables to predicates; this assignment determines
 * the head of the current category, as well as the fillers for
 * unfilled dependencies.
 * 
 * @author jayant
 */
public class CcgCategory implements Serializable {
  private static final long serialVersionUID = 1L;

  // NOTE: Remember to change .equals() and .hashCode() if these
  // members are modified.

  // The syntactic type of this category
  private final HeadedSyntacticCategory syntax;

  // The semantic dependencies of this category, both filled and
  // unfilled.
  private final List<String> subjects;
  private final List<Integer> argumentNumbers;
  // The variables each dependency accepts.
  private final List<Integer> objects;

  // An assignment to the semantic variables of the syntactic
  // category. 
  // TODO: Set is the wrong representation! Must allow duplicate elements.
  private final List<Set<String>> variableAssignments;

  private static final char ENTRY_DELIMITER = ',';
  private static final char DEPENDENCY_DELIMITER = '#';

  public CcgCategory(HeadedSyntacticCategory syntax, List<String> subjects,
      List<Integer> argumentNumbers, List<Integer> objects, List<Set<String>> variableAssignments) {
    this.syntax = Preconditions.checkNotNull(syntax);

    this.subjects = ImmutableList.copyOf(subjects);
    this.argumentNumbers = ImmutableList.copyOf(argumentNumbers);
    this.objects = ImmutableList.copyOf(objects);

    this.variableAssignments = Preconditions.checkNotNull(variableAssignments);
  }

  /**
   * Parses a CCG category from a string. The format is:
   * <p>
   * (#-separated head word list),(syntactic type),(#-separated
   * dependency list).
   * <p>
   * For example, the category for "in" is: "in,(N\N)/N,in 1 ?1, in 2
   * ?2".
   * 
   * @param categoryString
   * @return
   */
  public static CcgCategory parseFrom(String categoryString) {
    try {
      String[] parts = new CSVParser(ENTRY_DELIMITER, CSVParser.DEFAULT_QUOTE_CHARACTER, 
          CSVParser.NULL_CHARACTER).parseLine(categoryString);
      Preconditions.checkArgument(parts.length >= 1, "Invalid CCG category string: %s",
          categoryString);
      if (parts.length > 2) {
        return parseFrom(parts[0], parts[1], parts[2]);
      } else if (parts.length > 1) {
        return parseFrom(parts[0], parts[1], "");
      } else {
        return parseFrom(parts[0], "", "");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid CCG category: " + categoryString, e);
    }
  }

  public static CcgCategory parseFrom(String syntaxString, String assignmentString, String dependencyString) {
    try {
      // Parse the syntactic category.
      HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom(syntaxString);

      CSVParser dependencyParser = new CSVParser(DEPENDENCY_DELIMITER);

      // Parse any value assignments to variables.
      String[] assignmentStrings = dependencyParser.parseLine(assignmentString);
      List<Set<String>> values = Lists.newArrayList();
      for (int i = 0; i < syntax.getUniqueVariables().length; i++) {
        values.add(Sets.<String>newHashSet());
      }
      if (assignmentString.trim().length() > 0) {
        for (int i = 0; i < assignmentStrings.length; i++) {
          String[] parts = assignmentStrings[i].split("\\s+");
          Preconditions.checkArgument(parts.length == 2, "Invalid CCG variable assignment: %s", 
              assignmentStrings[i]);
          
          int varNum = Integer.parseInt(parts[0]);
          String value = parts[1];
          values.get(varNum).add(value);
        }
      }
      
      // Parse any semantic dependencies.
      List<String> subjects = Lists.newArrayList();
      List<Integer> argumentNumbers = Lists.newArrayList();
      List<Integer> objects = Lists.newArrayList();
      if (dependencyString.trim().length() > 0) {
        String[] depStrings = dependencyParser.parseLine(dependencyString);
        for (int i = 0; i < depStrings.length; i++) {
          String[] depParts = depStrings[i].split(" ");
          Preconditions.checkArgument(depParts.length == 3, "Invalid CCG semantic dependency: %s",
              depStrings[i]);

          subjects.add(depParts[0]);
          argumentNumbers.add(Integer.parseInt(depParts[1]));
          objects.add(Integer.parseInt(depParts[2]));
        }
      }

      return new CcgCategory(syntax, subjects, argumentNumbers, objects, values);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid CCG category: " + syntaxString + "," 
          + assignmentString + "," + dependencyString, e);
    }
  }

  /**
   * Gets the syntactic type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getSyntax() {
    return syntax;
  }

  /**
   * Gets the values assigned to each semantic variable of this. The
   * length of the returned list is equal to the number of semantic
   * variables, as returned by {@link #getSemanticVariables()}.
   * 
   * @return
   */
  public List<Set<String>> getAssignment() {
    return variableAssignments;
  }

  /**
   * Gets the set of semantic variables used in this category.
   * 
   * @return
   */
  public int[] getSemanticVariables() {
    return syntax.getUniqueVariables();
  }

  public List<String> getSubjects() {
    return subjects;
  }

  public List<Integer> getObjects() {
    return objects;
  }

  public List<Integer> getArgumentNumbers() {
    return argumentNumbers;
  }

  public List<UnfilledDependency> createUnfilledDependencies(int wordIndex,
      List<UnfilledDependency> filledDependencies) {
    List<UnfilledDependency> unfilledDependencies = Lists.newArrayListWithCapacity(subjects.size());
    for (int i = 0; i < subjects.size(); i++) {
      IndexedPredicate subject = new IndexedPredicate(subjects.get(i), wordIndex);
      UnfilledDependency dep = new UnfilledDependency(subject, -1, argumentNumbers.get(i),
          null, objects.get(i));

      // Technically, this is unnecessary since removing the possibility of pre-filled
      // dependencies. TODO: add back pre-filled dependencies.
      if (dep.isFilledDependency()) {
        filledDependencies.add(dep);
      } else {
        unfilledDependencies.add(dep);
      }
    }
    return unfilledDependencies;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentNumbers == null) ? 0 : argumentNumbers.hashCode());
    result = prime * result + ((objects == null) ? 0 : objects.hashCode());
    result = prime * result + ((subjects == null) ? 0 : subjects.hashCode());
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
    result = prime * result + ((variableAssignments == null) ? 0 : variableAssignments.hashCode());
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
    if (variableAssignments == null) {
      if (other.variableAssignments != null)
        return false;
    } else if (!variableAssignments.equals(other.variableAssignments))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return subjects.toString() + ":" + syntax.toString();
  }
}