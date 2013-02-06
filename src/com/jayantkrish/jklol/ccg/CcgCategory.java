package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.util.CsvParser;

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

  // The logical form for this category. May be null.
  private final Expression logicalForm;

  // The semantic dependencies of this category, both filled and
  // unfilled.
  private final List<String> subjects;
  private final List<Integer> argumentNumbers;
  // The variables each dependency accepts.
  private final List<Integer> objects;

  // An assignment to the semantic variables of the syntactic
  // category.
  // TODO: Set is the wrong representation! Must allow duplicate
  // elements.
  private final List<Set<String>> variableAssignments;

  private static final char ENTRY_DELIMITER = ',';

  /**
   * 
   * @param syntax
   * @param logicalForm
   * @param subjects
   * @param argumentNumbers
   * @param objects
   * @param variableAssignments
   */
  public CcgCategory(HeadedSyntacticCategory syntax, Expression logicalForm, List<String> subjects,
      List<Integer> argumentNumbers, List<Integer> objects, List<Set<String>> variableAssignments) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.logicalForm = logicalForm;

    this.subjects = ImmutableList.copyOf(subjects);
    this.argumentNumbers = ImmutableList.copyOf(argumentNumbers);
    this.objects = ImmutableList.copyOf(objects);

    this.variableAssignments = Preconditions.checkNotNull(variableAssignments);
  }

  /**
   * Parses a CCG category from a string. The expected format is a
   * comma separated list:
   * 
   * <ol>
   * <li>Syntactic type, formatted as for a
   * {@link HeadedSyntacticCategory}.
   * <li>Logical form, formatted as for {@link ExpressionParser}. May
   * be the empty string, in which case no logical form is used.
   * <li>A list of variable assignments or unfilled dependencies:
   * <ul>
   * <li>Variable assignments are of the format
   * "(variable number) (value)".
   * <li>Unfilled dependencies are of the format
   * "(predicate name) (argument number) (variable number)".
   * </ul>
   * <p>
   * For example, the category for "in" is: <code>
   * ((N{1}\N{1}){0}/N{2}){0},0 in,in 1 1,in 2 2
   * </code>
   * 
   * @param categoryString
   * @return
   */
  public static CcgCategory parseFrom(String categoryString) {
    String[] parts = new CsvParser(ENTRY_DELIMITER, CsvParser.DEFAULT_QUOTE,
        CsvParser.NULL_ESCAPE).parseLine(categoryString);
    Preconditions.checkArgument(parts.length >= 1, "Invalid CCG category string: %s",
        categoryString);
    return parseFrom(parts);
  }

  public static CcgCategory parseFrom(String[] categoryParts) {
    // Parse the syntactic category and store it in canonical form.
    HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom(categoryParts[0]);
    Map<Integer, Integer> relabeling = Maps.newHashMap();
    syntax = syntax.getCanonicalForm(relabeling);

    Expression logicalForm = null;
    if (categoryParts[1].trim().length() > 0) {
      logicalForm = (new ExpressionParser()).parseSingleExpression(categoryParts[1]);
    } else {
      logicalForm = induceLogicalFormFromSyntax(syntax);
    }

    // Create an empty assignment to each variable in the syntactic
    // category.
    List<Set<String>> values = Lists.newArrayList();
    for (int i = 0; i < syntax.getUniqueVariables().length; i++) {
      values.add(Sets.<String> newHashSet());
    }
    // Create empty set of unfilled dependencies.
    List<String> subjects = Lists.newArrayList();
    List<Integer> argumentNumbers = Lists.newArrayList();
    List<Integer> objects = Lists.newArrayList();

    // Parse any value assignments to variables and unfilled
    // dependencies.
    for (int i = 2; i < categoryParts.length; i++) {
      if (categoryParts[i].trim().length() == 0) {
        continue;
      }

      String[] parts = categoryParts[i].trim().split("\\s+");
      Preconditions.checkArgument(parts.length == 2 || parts.length == 3,
          "Invalid CCG semantic part: \"%s\".", categoryParts[i]);
      if (parts.length == 2) {
        int originalVarNum = Integer.parseInt(parts[0]);
        Preconditions.checkArgument(relabeling.containsKey(originalVarNum),
            "Illegal assignment \"%s\" for syntactic category %s", categoryParts[i],
            categoryParts[0]);
        int varNum = relabeling.get(originalVarNum);
        String value = parts[1];
        values.get(varNum).add(value);
      } else if (parts.length == 3) {
        subjects.add(parts[0]);
        argumentNumbers.add(Integer.parseInt(parts[1]));
        int originalVarNum = Integer.parseInt(parts[2]);
        Preconditions.checkArgument(relabeling.containsKey(originalVarNum),
            "Illegal dependency \"%s\" for syntactic category %s", categoryParts[i],
            categoryParts[0]);
        int objectVarNum = relabeling.get(originalVarNum);
        objects.add(objectVarNum);
      }
    }

    return new CcgCategory(syntax, logicalForm, subjects, argumentNumbers, objects, values);
  }
  
  private static Expression induceLogicalFormFromSyntax(HeadedSyntacticCategory syntax) {
    int[] syntaxVarNums = syntax.getUniqueVariables();
    Map<Integer, ConstantExpression> varMap = Maps.newHashMap();
    for (int i = 0; i < syntaxVarNums.length; i++) {
      varMap.put(i, new ConstantExpression("$" + syntaxVarNums[i]));
    }

    List<HeadedSyntacticCategory> argumentCats = Lists.newArrayList();
    List<Integer> argumentRoots = Lists.newArrayList();
    List<ConstantExpression> argumentVariables = Lists.newArrayList();
    System.out.println(syntax);
    while (!syntax.isAtomic()) {
      HeadedSyntacticCategory argument = syntax.getArgumentType();
      argumentCats.add(argument);
      argumentRoots.add(argument.getRootVariable());
      argumentVariables.add(varMap.get(argument.getRootVariable()));
      syntax = syntax.getReturnType();
    }

    System.out.println(syntax + " " + argumentVariables);

    Expression body = null;
    int argumentIndex = argumentRoots.indexOf(syntax.getRootVariable());
    if (argumentIndex != -1) {
      HeadedSyntacticCategory argument = argumentCats.get(argumentIndex);
      if (argument.isAtomic()) {
        body = varMap.get(argumentIndex);
      } else {
        List<Expression> argumentArguments = Lists.newArrayList();
        while (!argument.isAtomic()) {
          argumentArguments.add(varMap.get(argument.getArgumentType().getRootVariable()));
          argument = argument.getReturnType();
        }
        
        body = new ApplicationExpression(varMap.get(argumentIndex), argumentArguments);
      }
    } else {
      body = new ConstantExpression("$" + syntax.getRootVariable());
    }
    return new LambdaExpression(argumentVariables, body);
  }

  /**
   * Gets the syntactic type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getSyntax() {
    return syntax;
  }
  
  public Expression getLogicalForm() {
    return logicalForm;
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

  /**
   * The semantic head of this category, i.e., the assignment to the
   * semantic variable at the root of the syntactic tree.
   * 
   * @return
   */
  public List<String> getSemanticHeads() {
    int headSemanticVariable = syntax.getRootVariable();
    int[] allSemanticVariables = getSemanticVariables();
    for (int i = 0; i < allSemanticVariables.length; i++) {
      if (allSemanticVariables[i] == headSemanticVariable) {
        return Lists.newArrayList(variableAssignments.get(i));
      }
    }
    return Collections.emptyList();
  }

  /**
   * Gets the subjects of any unfilled dependencies projected by this
   * category.
   * 
   * @return
   */
  public List<String> getSubjects() {
    return subjects;
  }

  /**
   * Gets the objects of any unfilled dependencies projected by this
   * category. Each returned integer is a semantic variable in this
   * category, as given by {@link #getSemanticVariables()}.
   * 
   * @return
   */
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

      // Technically, this is unnecessary since removing the
      // possibility of pre-filled dependencies. TODO: add back
      // pre-filled dependencies.
      if (dep.isFilledDependency()) {
        filledDependencies.add(dep);
      } else {
        unfilledDependencies.add(dep);
      }
    }
    return unfilledDependencies;
  }

  @Override
  public String toString() {
    return getSemanticHeads() + ":" + syntax.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((argumentNumbers == null) ? 0 : argumentNumbers.hashCode());
    result = prime * result + ((logicalForm == null) ? 0 : logicalForm.hashCode());
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
    if (logicalForm == null) {
      if (other.logicalForm != null)
        return false;
    } else if (!logicalForm.equals(other.logicalForm))
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
}