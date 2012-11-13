package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;

/**
 * A semantic category augmented with semantic variables. These
 * variables determine the head word of each subcategory (i.e.,
 * head-passing rules), and also serve as the arguments of semantic
 * dependencies.
 * 
 * @author jayantk
 */
public class HeadedSyntacticCategory implements Serializable {

  private static final long serialVersionUID = 1L;

  private final SyntacticCategory syntacticCategory;
  // Semantic variable for each subcategory of syntacticCategory,
  // where the ith entry represents the variable for the i'th
  // subcategory in an in-order traversal of syntacticCategory.
  private final int[] semanticVariables;
  // Position of the root node in semanticVariables.
  private final int rootIndex;

  public HeadedSyntacticCategory(SyntacticCategory syntacticCategory,
      int[] semanticVariables, int rootIndex) {
    this.syntacticCategory = syntacticCategory;
    this.semanticVariables = semanticVariables;
    this.rootIndex = rootIndex;
  }

  /**
   * Parses a syntactic category with augmented semantic variable
   * information from a category string. The expected format is
   * identical to that for {@link SyntacticCategory}, except that each
   * parenthesized or atomic element may be followed by a semantic
   * variable number in curly braces ({}). All elements with the same
   * number will be semantically unified during parsing. Unnumbered
   * elements will be assigned their own semantic variable (with an
   * unpredictable number).
   * <p>
   * If a category contains {@code n} semantic variables, they must be
   * numbered {@code 0,1,...,n-1}.
   * <p>
   * Examples:<br>
   * that := ((N{1}\N{1}){0}/(S\N{1})){0} <br>
   * very := ((N{1}/N{1}){2}/(N{1}/N{1}){2}){0}
   * 
   * @param typeString
   * @return
   */
  public static HeadedSyntacticCategory parseFrom(String typeString) {
    Pattern pattern = Pattern.compile("\\{[^}]*\\}");
    String syntacticString = pattern.matcher(typeString).replaceAll("");
    SyntacticCategory syntax = SyntacticCategory.parseFrom(syntacticString);
    int[] semanticVariables = new int[syntax.getNumSubcategories()];
    int rootIndex = syntax.getNumReturnSubcategories();

    parseSemanticVariables(syntax, typeString, semanticVariables, 0);
    return new HeadedSyntacticCategory(syntax, semanticVariables, rootIndex);
  }

  private static void parseSemanticVariables(SyntacticCategory category,
      String typeString, int[] semanticVariables, int firstFillableIndex) {

    int curIndex;
    if (!category.isAtomic()) {
      curIndex = firstFillableIndex + category.getNumReturnSubcategories();
    } else {
      curIndex = firstFillableIndex;
    }

    int lastLeftBraceIndex = typeString.lastIndexOf('{');
    int lastRightBraceIndex = typeString.lastIndexOf('}');
    int lastParenIndex = typeString.lastIndexOf(')');

    if (lastLeftBraceIndex > lastParenIndex) {
      semanticVariables[curIndex] = Integer.parseInt(typeString.substring(
          lastLeftBraceIndex + 1, lastRightBraceIndex));
    } else {
      semanticVariables[curIndex] = -1;
    }

    if (!category.isAtomic()) {
      int splitIndex = SyntacticCategory.findSlashIndex(typeString);

      if (lastParenIndex != -1) {
        parseSemanticVariables(category.getArgument(), typeString.substring(splitIndex + 1, lastParenIndex),
            semanticVariables, curIndex + 1);
      } else {
        parseSemanticVariables(category.getArgument(), typeString.substring(splitIndex + 1),
            semanticVariables, curIndex + 1);
      }
      parseSemanticVariables(category.getReturn(), typeString.substring(0, splitIndex),
          semanticVariables, firstFillableIndex);
    }
  }

  /**
   * Gets the syntactic category of this, without any semantic
   * annotations.
   * 
   * @return
   */
  public SyntacticCategory getSyntax() {
    return syntacticCategory;
  }

  /**
   * Gets the syntactic type and semantic variable assignments to the
   * argument type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getArgumentType() {
    SyntacticCategory argumentSyntax = syntacticCategory.getArgument();
    int[] argumentSemantics = Arrays.copyOfRange(semanticVariables, rootIndex + 1, semanticVariables.length);
    int argumentRoot = argumentSyntax.getNumReturnSubcategories();
    return new HeadedSyntacticCategory(argumentSyntax, argumentSemantics, argumentRoot);
  }

  /**
   * Gets the syntactic type and semantic variable assignments to the
   * return type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getReturnType() {
    SyntacticCategory returnSyntax = syntacticCategory.getReturn();
    int[] returnSemantics = Arrays.copyOf(semanticVariables, rootIndex);
    int returnRoot = returnSyntax.getNumReturnSubcategories();
    return new HeadedSyntacticCategory(returnSyntax, returnSemantics, returnRoot);
  }

  /**
   * Gets the syntactic category and semantic variables that accepts
   * {@code argument} on {@code direction} and returns {@code this}.
   * Any semantic variables in both {@code argument} and {@code this}
   * are assumed to be equivalent.
   * 
   * @param argument
   * @param direction
   * @param headVarNum
   * @return
   */
  public HeadedSyntacticCategory addArgument(HeadedSyntacticCategory argument, Direction direction,
      int headVarNum) {
    SyntacticCategory newCategory = syntacticCategory.addArgument(argument.getSyntax(), direction);
    int[] newSemantics = Ints.concat(semanticVariables, new int[] { headVarNum },
        argument.semanticVariables);
    int newRoot = semanticVariables.length;

    return new HeadedSyntacticCategory(newCategory, newSemantics, newRoot);
  }

  /**
   * Gets the variable assigned to the root node of {@code this}
   * category.
   * 
   * @return
   */
  public int getRootVariable() {
    return semanticVariables[rootIndex];
  }

  public HeadedSyntacticCategory relabelVariables(int[] currentVars, int[] relabeledVars) {
    Preconditions.checkArgument(currentVars.length == relabeledVars.length);

    int[] newSemanticVariables = new int[semanticVariables.length];
    for (int i = 0; i < semanticVariables.length; i++) {
      int varIndex = Ints.indexOf(currentVars, semanticVariables[i]);
      newSemanticVariables[i] = relabeledVars[varIndex];
    }

    return new HeadedSyntacticCategory(syntacticCategory, newSemanticVariables, rootIndex);
  }

  /**
   * Maps each variable in {@code uniqueVars} to a unique variable in
   * {@code other}. {@code uniqueVars} is a set of variables which may
   * occur in {@code this}. The mapping must be one-to-one, and this
   * method returns {@code null} if no one-to-one mapping exists
   * (i.e., if some variable must map to multiple variables.).
   * <p>
   * The returned list is a relabeling of each variable in
   * {@code uniqueVars}. The ith element of the returned list is the
   * relabeled variable for the ith element of {@code uniqueVars}. If
   * a variable in {@code uniqueVars} is not mapped to a variable in
   * {@code other}, its relabeling is equal to an arbitrary number not
   * in {@code assignedVars}.
   * 
   * @param uniqueVars
   * @param other
   * @return
   */
  public int[] unifyVariables(int[] uniqueVars, HeadedSyntacticCategory other, int[] assignedVars) {
    Preconditions.checkArgument(syntacticCategory.isUnifiableWith(other.getSyntax()));
    int[] otherSemanticVariables = other.semanticVariables;
    Preconditions.checkArgument(otherSemanticVariables.length == semanticVariables.length);

    int[] mapping = new int[uniqueVars.length];
    Arrays.fill(mapping, -1);

    for (int i = 0; i < semanticVariables.length; i++) {
      int curVar = semanticVariables[i];
      if (curVar != -1) {
        int curVarIndex = Ints.indexOf(uniqueVars, curVar);
        int existingMapping = mapping[curVarIndex];
        int curMapping = otherSemanticVariables[i];
        if (curMapping == -1) {
          continue;
        } else if (existingMapping == -1) {
          mapping[curVarIndex] = curMapping;
        } else if (curMapping != existingMapping) {
          return null;
        }
      }
    }
    
    // Check that mapping contains no duplicate elements.
    int[] sortedMapping = Arrays.copyOf(mapping, mapping.length);
    Arrays.sort(sortedMapping);
    for (int i = 1; i < sortedMapping.length; i++) {
      if (sortedMapping[i - 1] == sortedMapping[i]) {
        return null;
      }
    }

    int maxAssigned = assignedVars.length != 0 ? Ints.max(assignedVars) : 0;
    int maxUnique = other.getUniqueVariables().length != 0 ? Ints.max(other.getUniqueVariables()) : 0;
    int nextVar = Math.max(maxAssigned, maxUnique) + 1;
    for (int i = 0; i < mapping.length; i++) {
      if (mapping[i] == -1) {
        mapping[i] = nextVar;
        nextVar++;
      }
    }

    return mapping;
  }

  /**
   * Gets the set of unique semantic variables referenced in this
   * category. (Each variable is represented by a unique integer).
   * 
   * @return
   */
  public int[] getUniqueVariables() {
    Set<Integer> uniqueVars = Sets.newTreeSet(Ints.asList(semanticVariables));

    int[] uniqueVarsArray = new int[uniqueVars.size()];
    int i = 0;
    for (Integer uniqueVar : uniqueVars) {
      uniqueVarsArray[i] = uniqueVar;
      i++;
    }

    return uniqueVarsArray;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + rootIndex;
    result = prime * result + Arrays.hashCode(semanticVariables);
    result = prime * result + ((syntacticCategory == null) ? 0 : syntacticCategory.hashCode());
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
    HeadedSyntacticCategory other = (HeadedSyntacticCategory) obj;
    if (rootIndex != other.rootIndex)
      return false;
    if (!Arrays.equals(semanticVariables, other.semanticVariables))
      return false;
    if (syntacticCategory == null) {
      if (other.syntacticCategory != null)
        return false;
    } else if (!syntacticCategory.equals(other.syntacticCategory))
      return false;
    return true;
  }

  @Override
  public String toString() {
    if (syntacticCategory.isAtomic()) {
      return syntacticCategory.toString() + "_" + semanticVariables[rootIndex];
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append("(");
      sb.append(getReturnType());
      sb.append(syntacticCategory.getDirection());
      sb.append(getArgumentType());
      sb.append(")_");
      sb.append(semanticVariables[rootIndex]);
      return sb.toString();
    }
  }
}
