package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.util.ArrayUtils;

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

  public HeadedSyntacticCategory(SyntacticCategory syntacticCategory, int[] semanticVariables,
      int rootIndex) {        
    this.syntacticCategory = Preconditions.checkNotNull(syntacticCategory);
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
   * elements will be assigned to semantic variable 0, which typically
   * denotes the head of the syntactic category.
   * <p>
   * Furthermore, components of the category string may be annotated 
   * with syntactic subcategorization features in square braces ([]).
   * These features may be passed to other syntactic variables via 
   * unification during parsing, in the same fashion as semantic 
   * variables. If included, these features must be placed before 
   * the semantic variable number, e.g., N[athlete]{1}.    
   * <p>
   * Examples:<br>
   * that := ((N{1}\N{1}){0}/(S\N{1})){0} <br>
   * very := ((N{1}/N{1}){2}/(N{1}/N{1}){2}){0}
   * going := ((S[ing]{0}\N{1}){0}/N{2}){0}
   * 
   * @param typeString
   * @return
   */
  public static HeadedSyntacticCategory parseFrom(String typeString) {
    // Strip variable numbers and features, without using 
    // regular expressions for GWT compatibility.
    String syntacticString = stripBracketedExpression(typeString, "{", "}");

    SyntacticCategory syntax = SyntacticCategory.parseFrom(syntacticString);
    int[] semanticVariables = new int[syntax.getNumSubcategories()];
    parseSemanticVariables(syntax, typeString, semanticVariables, 0);

    int rootIndex = syntax.getNumReturnSubcategories();
    return new HeadedSyntacticCategory(syntax, semanticVariables, rootIndex);
  }
  
  private static String stripBracketedExpression(String string, String leftBracket, 
      String rightBracket) {
    Preconditions.checkArgument(leftBracket.length() == 1);
    Preconditions.checkArgument(rightBracket.length() == 1);

    StringBuilder syntacticStringBuilder = new StringBuilder();
    int nextBraceIndex = 0;
    int braceIndex = string.indexOf(leftBracket, nextBraceIndex);
    while (braceIndex != -1) {
      syntacticStringBuilder.append(string.substring(nextBraceIndex, braceIndex));
      nextBraceIndex = string.indexOf(rightBracket, braceIndex) + 1;
      braceIndex = string.indexOf(leftBracket, nextBraceIndex);
    }
    syntacticStringBuilder.append(string.substring(nextBraceIndex, string.length()));
    return syntacticStringBuilder.toString();
  }

  private static void parseSemanticVariables(SyntacticCategory category, String typeString,
      int[] semanticVariables, int firstFillableIndex) {
    int curIndex;
    if (!category.isAtomic()) {
      curIndex = firstFillableIndex + category.getNumReturnSubcategories();
    } else {
      curIndex = firstFillableIndex;
    }

    int lastLeftBraceIndex = typeString.lastIndexOf('{');
    int lastRightBraceIndex = typeString.lastIndexOf('}');

    if (category.isAtomic()) {
      if (lastLeftBraceIndex != -1) {
        semanticVariables[curIndex] = Integer.parseInt(typeString.substring(
            lastLeftBraceIndex + 1, lastRightBraceIndex));
      } else {
        semanticVariables[curIndex] = 0;
      }
    } else {
      int splitIndex = SyntacticCategory.findSlashIndex(typeString);
      int lastParenIndex = -1;
      // Determine if this category is wrapped in parentheses:
      String argumentString = typeString.substring(splitIndex + 1);
      int leftParenCount = 0;
      int rightParenCount = 0;
      for (int i = 0; i < argumentString.length(); i++) {
        if (argumentString.charAt(i) == '(') {
          leftParenCount++;
        } else if (argumentString.charAt(i) == ')') {
          rightParenCount++;
        }
      }
      if (rightParenCount > leftParenCount) {
        lastParenIndex = typeString.lastIndexOf(')');
      }

      if (lastParenIndex != -1) {
        if (lastLeftBraceIndex != -1 && lastLeftBraceIndex > lastParenIndex) {
          semanticVariables[curIndex] = Integer.parseInt(typeString.substring(
              lastLeftBraceIndex + 1, lastRightBraceIndex));
        } else {
          semanticVariables[curIndex] = 0;
        }

        parseSemanticVariables(category.getArgument(), typeString.substring(splitIndex + 1, lastParenIndex),
            semanticVariables, curIndex + 1);
      } else {
        semanticVariables[curIndex] = 0;
        parseSemanticVariables(category.getArgument(), typeString.substring(splitIndex + 1),
            semanticVariables, curIndex + 1);
      }
      parseSemanticVariables(category.getReturn(), typeString.substring(0, splitIndex),
          semanticVariables, firstFillableIndex);
    }
  }

  public static List<SyntacticCategory> convertToCcgbank(List<HeadedSyntacticCategory> headedCats) {
    List<SyntacticCategory> cats = Lists.newArrayList();
    for (HeadedSyntacticCategory headedCat : headedCats) {
      cats.add(headedCat.getSyntax().discardFeaturePassingMarkup());
    }
    return cats;
  }

  /**
   * Version of {@link #getCanonicalForm()} that does not return the
   * relabeling.
   * 
   * @return
   */
  public HeadedSyntacticCategory getCanonicalForm() {
    return getCanonicalForm(Maps.<Integer, Integer> newHashMap());
  }
  
  /**
   * Returns {@code true} if this category is in canonical form.
   * 
   * @return {@code true} if this category is in canonical form.
   */
  public boolean isCanonicalForm() {
    return getCanonicalForm().equals(this);
  }
  
  private int[] canonicalizeVariableArray(int[] variableArray, Map<Integer, Integer> relabeling) {
    int[] relabeledVariables = new int[variableArray.length];
    for (int i = 0; i < variableArray.length; i++) {
      int curVar = variableArray[i];
      if (curVar == -1) {
        relabeledVariables[i] = -1;
        continue;
      }
      
      int relabeledVar = -1;
      if (!relabeling.containsKey(curVar)) {
        relabeledVar = relabeling.size();
        relabeling.put(curVar, relabeling.size());
      } else {
        relabeledVar = relabeling.get(curVar);
      }

      relabeledVariables[i] = relabeledVar;
    }
    return relabeledVariables;
  }

  /**
   * Gets a canonical representation of this category that treats each
   * variable number as an equivalence class. The canonical form
   * relabels variables in the category such that all categories with
   * the same variable equivalence relations have the same canonical
   * form.
   * 
   * @param relabeling the relabeling applied to variables in this to
   * produce the relabeled category.
   * @return
   */
  public HeadedSyntacticCategory getCanonicalForm(Map<Integer, Integer> relabeling) {
    Preconditions.checkArgument(relabeling.size() == 0);
    int[] relabeledVariables = canonicalizeVariableArray(semanticVariables, relabeling);
    return new HeadedSyntacticCategory(syntacticCategory.getCanonicalForm(), relabeledVariables, rootIndex);
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
  
  public Direction getDirection() {
    return syntacticCategory.getDirection();
  }
  
  public boolean isAtomic() {
    return syntacticCategory.isAtomic();
  }
  
  /**
   * Gets the syntactic type and semantic variable assignments to the
   * argument type of this category.
   * 
   * @return
   */
  public HeadedSyntacticCategory getArgumentType() {
    SyntacticCategory argumentSyntax = syntacticCategory.getArgument();
    int[] argumentSemantics = ArrayUtils.copyOfRange(semanticVariables, rootIndex + 1, semanticVariables.length);
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
    int[] returnSemantics = ArrayUtils.copyOf(semanticVariables, rootIndex);
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
   * @param rootVarNum
   * @return
   */
  public HeadedSyntacticCategory addArgument(HeadedSyntacticCategory argument, Direction direction,
      int rootVarNum) {
    SyntacticCategory newCategory = syntacticCategory.addArgument(argument.getSyntax(), direction);
    int[] newSemantics = Ints.concat(semanticVariables, new int[] { rootVarNum },
        argument.semanticVariables);
    int newRoot = semanticVariables.length;
    return new HeadedSyntacticCategory(newCategory, newSemantics, newRoot);
  }

  /**
   * Gets the variable representing the semantic head of this category.
   * 
   * @return
   */
  public int getHeadVariable() {
    return semanticVariables[rootIndex];
  }
  
  public String getRootFeature() {
    return syntacticCategory.getRootFeature();
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
    Preconditions.checkArgument(other.isUnifiableWith(this), "Not unifiable: " + this + " and " + other);
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
    int[] sortedMapping = ArrayUtils.copyOf(mapping, mapping.length);
    Arrays.sort(sortedMapping);
    for (int i = 1; i < sortedMapping.length; i++) {
      if (sortedMapping[i] != -1 && sortedMapping[i - 1] == sortedMapping[i]) {
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
  
  public boolean isUnifiableWith(HeadedSyntacticCategory other) {
    Map<Integer, String> myAssignedVariables = Maps.newHashMap();
    Map<Integer, String> otherAssignedVariables = Maps.newHashMap();
    Map<Integer, Integer> variableRelabeling = Maps.newHashMap();

    return isUnifiableWith(other, myAssignedVariables, otherAssignedVariables, variableRelabeling); 
  }
  
  public boolean isUnifiableWith(HeadedSyntacticCategory other, Map<Integer, String> assignedFeatures,
       Map<Integer, String> otherAssignedFeatures, Map<Integer, Integer> relabeledFeatures) {
    HeadedSyntacticCategory thisCanonical = getCanonicalForm();
    HeadedSyntacticCategory otherCanonical = other.getCanonicalForm();

    if (!(thisCanonical.rootIndex == otherCanonical.rootIndex)) {
      return false;
    } else if (!thisCanonical.syntacticCategory.isUnifiableWith(otherCanonical.syntacticCategory, 
        assignedFeatures, otherAssignedFeatures, relabeledFeatures)) {
      return false;
    } else if (!Arrays.equals(thisCanonical.semanticVariables, otherCanonical.semanticVariables)) {
      return false;
    }

    return true;
  }

  public HeadedSyntacticCategory assignFeatures(Map<Integer, String> assignedFeatures,
      Map<Integer, Integer> relabeledFeatures) {
    return new HeadedSyntacticCategory(syntacticCategory.assignFeatures(
        assignedFeatures, relabeledFeatures), semanticVariables, rootIndex);
  }

  public HeadedSyntacticCategory assignAllFeatures(String value) {
    return new HeadedSyntacticCategory(syntacticCategory.assignAllFeatures(value),
        semanticVariables, rootIndex);
  }

  /**
   * Gets all syntactic categories which can be formed by assigning values to the
   * feature variables of this category. Returned categories may not be in canonical
   * form.
   * 
   * @param featureValues
   * @return
   */
  public Set<HeadedSyntacticCategory> getSubcategories(Set<String> featureValues) {
    Set<HeadedSyntacticCategory> subcategories = Sets.newHashSet();
    for (SyntacticCategory newSyntax : syntacticCategory.getSubcategories(featureValues)) {
      subcategories.add(new HeadedSyntacticCategory(newSyntax, semanticVariables, rootIndex));
    }
    return subcategories;
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
    result = prime * result
        + ((syntacticCategory == null) ? 0 : syntacticCategory.hashCode());
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
    StringBuilder sb = new StringBuilder();
    if (syntacticCategory.isAtomic()) {
      sb.append(syntacticCategory.getValue());
    } else {
      sb.append("(");
      sb.append(getReturnType());
      sb.append(syntacticCategory.getDirection());
      sb.append(getArgumentType());
      sb.append(")");
    }

    if (!syntacticCategory.getRootFeature().equals(SyntacticCategory.DEFAULT_FEATURE_VALUE)) {
      sb.append("[");
      sb.append(syntacticCategory.getRootFeature());
      sb.append("]");
    } else if (syntacticCategory.getRootFeatureVariable() != -1) {
      sb.append("[");
      sb.append(syntacticCategory.getRootFeatureVariable());
      sb.append("]");
    }

    sb.append("{");
    sb.append(semanticVariables[rootIndex]);
    sb.append("}");
    return sb.toString();
  }
}