package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

/**
 * A CCG category is a tuple of a syntactic category and semantic
 * representation, as would appear on the right hand side of a CCG
 * lexicon entry.
 * <p>
 * {@code CcgCategory} contains two independent semantic representations.
 * The first is a dependency representation, as used in CCGbank. This
 * representation consists of (1) a set of unfilled dependencies, and
 * (2) a set of syntactic variable assignments. Each unfilled dependency
 * is a (subject, argument number, object) triple, where the subject is
 * the name of the predicate applied by this entry, the argument number is 
 * an argument to that predicate, and the object is a variable number in the
 * syntactic category whose assignment will be used to fill the dependency 
 * during parsing. The syntactic variable assignments are a mapping
 * from syntactic variables to predicates. This assignment determines
 * the fillers of dependencies during parsing.
 * <p>
 * The second semantic representation is a lambda calculus logical form.  
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
  private final Expression2 logicalForm;

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

  /**
   * 
   * @param syntax
   * @param logicalForm
   * @param subjects
   * @param argumentNumbers
   * @param objects
   * @param variableAssignments
   */
  public CcgCategory(HeadedSyntacticCategory syntax, Expression2 logicalForm, List<String> subjects,
      List<Integer> argumentNumbers, List<Integer> objects, List<Set<String>> variableAssignments) {
    this.syntax = Preconditions.checkNotNull(syntax);
    Preconditions.checkArgument(syntax.isCanonicalForm(),
        "Syntactic category must be in canonical form. Got: %s", syntax);
    this.logicalForm = logicalForm;

    this.subjects = ImmutableList.copyOf(subjects);
    this.argumentNumbers = ImmutableList.copyOf(argumentNumbers);
    this.objects = ImmutableList.copyOf(objects);

    this.variableAssignments = Preconditions.checkNotNull(variableAssignments);
    Preconditions.checkArgument(syntax.getUniqueVariables().length == variableAssignments.size(),
        "Invalid number of assignments for syntactic category %s", syntax);
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
   * ((N{1}\N{1}){0}/N{2}){0},,0 in,in 1 1,in 2 2
   * </code>
   * 
   * @param categoryString
   * @return
   */
  public static CcgCategory parseFrom(String categoryString) {
    String[] parts = LexiconEntry.getCsvParser().parseLine(
        categoryString);
    Preconditions.checkArgument(parts.length >= 1, "Invalid CCG category string: %s",
        categoryString);
    return parseFrom(parts);
  }

  public static CcgCategory parseFrom(String[] categoryParts) {
    // Parse the syntactic category and store it in canonical form.
    HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom(categoryParts[0]);
    Map<Integer, Integer> relabeling = Maps.newHashMap();
    syntax = syntax.getCanonicalForm(relabeling);

    Expression2 logicalForm = null;
    if (categoryParts[1].trim().length() > 0) {
      logicalForm = ExpressionParser.expression2().parse(categoryParts[1]);
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
        String value = parts[1].intern();
        values.get(varNum).add(value);
      } else if (parts.length == 3) {
        subjects.add(parts[0].intern());
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

  public static CcgCategory parseFromJson(String json) {
    ObjectMapper mapper = new ObjectMapper();

    try {
      return CcgCategory.parseFromJson(mapper.readTree(json));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static CcgCategory parseFromJson(JsonNode node) {
    // Parse the syntactic category and store it in canonical form.
    HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom(node.get("syntax").asText());
    Map<Integer, Integer> relabeling = Maps.newHashMap();
    syntax = syntax.getCanonicalForm(relabeling);

    Expression2 logicalForm = null;
    String expressionString = node.get("logicalForm").asText();
    if (expressionString.trim().length() > 0) {
      logicalForm = ExpressionParser.expression2().parse(expressionString);
    }

    // Create an empty assignment to each variable in the syntactic
    // category.
    List<Set<String>> values = Lists.newArrayList();
    for (int i = 0; i < syntax.getUniqueVariables().length; i++) {
      values.add(Sets.<String> newHashSet());
    }
    
    // Parse assignments, if any are given.
    if (node.has("assignments")) {
      for (JsonNode assignment : node.get("assignments")) {
        int varNum = assignment.get("num").asInt();
        String value = assignment.get("value").asText().intern();
        values.get(relabeling.get(varNum)).add(value);
      }
    }
    
    // Create empty set of unfilled dependencies.
    List<String> subjects = Lists.newArrayList();
    List<Integer> argumentNumbers = Lists.newArrayList();
    List<Integer> objects = Lists.newArrayList();

    // Parse any unfilled dependencies, if given.
    if (node.has("dependencies")) {
      for (JsonNode dependency : node.get("dependencies")) {
        String head = dependency.get("head").asText().intern();
        int argNum = dependency.get("argNum").asInt();
        int varNum = dependency.get("varNum").asInt();
        
        subjects.add(head);
        argumentNumbers.add(argNum);
        objects.add(relabeling.get(varNum));
      }
    }

    return new CcgCategory(syntax, logicalForm, subjects, argumentNumbers, objects, values);
  }
  
  /**
   * Generates a CCG category with head and dependency information
   * automatically populated from the syntactic category and 
   * logical form. The logical form itself is used as the semantic
   * head of the returned category. 
   * 
   * @param cat
   * @param logicalForm
   * @return
   */
  public static CcgCategory fromSyntaxLf(HeadedSyntacticCategory cat, Expression2 lf) {
    String head = lf.toString();
    head = head.replaceAll(" ", "_");
    List<String> subjects = Lists.newArrayList();
    List<Integer> argumentNums = Lists.newArrayList();
    List<Integer> objects = Lists.newArrayList();
    List<Set<String>> assignments = Lists.newArrayList();
    assignments.add(Sets.newHashSet(head));

    List<HeadedSyntacticCategory> argumentCats = Lists.newArrayList(cat.getArgumentTypes());
    Collections.reverse(argumentCats);
    for (int i = 0; i < argumentCats.size(); i++) {
      subjects.add(head);
      argumentNums.add(i + 1);
      objects.add(argumentCats.get(i).getHeadVariable());
    }

    for (int i = 0; i < cat.getUniqueVariables().length - 1; i++) {
      assignments.add(Collections.<String>emptySet());
    }

    return new CcgCategory(cat, lf, subjects, argumentNums, objects, assignments);
  }

  /**
   * Gets the syntactic type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getSyntax() {
    return syntax;
  }
  
  public Expression2 getLogicalForm() {
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
    int headSemanticVariable = syntax.getHeadVariable();
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

  public CcgCategory replaceLogicalForm(Expression2 newLogicalForm) {
    return new CcgCategory(syntax, newLogicalForm, subjects, argumentNumbers, objects,
        variableAssignments);
  }

  public List<UnfilledDependency> createUnfilledDependencies(int wordIndex,
      List<UnfilledDependency> filledDependencies) {
    List<UnfilledDependency> unfilledDependencies = Lists.newArrayListWithCapacity(subjects.size());
    for (int i = 0; i < subjects.size(); i++) {
      IndexedPredicate subject = new IndexedPredicate(subjects.get(i), wordIndex);
      UnfilledDependency dep = new UnfilledDependency(subject, syntax, -1, argumentNumbers.get(i),
          null, objects.get(i));

      // Technically, this is unnecessary since removing the
      // possibility of pre-filled dependencies.
      if (dep.isFilledDependency()) {
        filledDependencies.add(dep);
      } else {
        unfilledDependencies.add(dep);
      }
    }
    return unfilledDependencies;
  }
  
  public String toCsvString() {
    List<String> parts = Lists.newArrayList();
    parts.add(syntax.toString());
    parts.add(logicalForm != null ? logicalForm.toString() : "");
    for (int i = 0; i < variableAssignments.size(); i++) {
      for (String assignment : variableAssignments.get(i)) {
        parts.add(i + " " + assignment);
      }
    }
    
    for (int i = 0; i < subjects.size(); i++) {
      parts.add(subjects.get(i) + " " + argumentNumbers.get(i) + " " + objects.get(i));
    }

    return LexiconEntry.getCsvParser().toCsv(parts);
  }

  @Override
  public String toString() {
    return toCsvString();
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