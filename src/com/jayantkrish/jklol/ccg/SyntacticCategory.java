package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A syntactic category for a CCG, such as N/N. These categories
 * represent syntactic function/argument information. Each category is
 * either atomic (e.g., N, S) or functional (e.g., N/N, (S\N)/N).
 * Functional categories have an argument type to which they can be
 * applied, and a return type which results from application.
 * 
 * @author jayant
 */
public class SyntacticCategory implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum Direction {
    LEFT("\\"), RIGHT("/");

    private final String slash;

    private Direction(final String slash) {
      this.slash = slash;
    }

    @Override
    public String toString() {
      return slash;
    }
  };
  // The feature value assigned to syntactic categories for which
  // no feature value is specified.
  public static final String DEFAULT_FEATURE_VALUE = "";

  // If this category is atomic, then it has a value.
  private final String value;

  // If this category is functional, it has an argument direction, an
  // argument and a return type.
  private final Direction direction;
  private final SyntacticCategory returnType;
  private final SyntacticCategory argumentType;
  
  // Either type of category may have an associated feature. No feature
  // is encoded as DEFAULT_FEATURE_VALUE. Features may be matched using a 
  // feature variable.
  private final String featureValue;
  private final int featureVariable;
  
  private int cachedHashCode;

  private static final Map<String, SyntacticCategory> categoryInternmentMap =
      Maps.newHashMap();

  private SyntacticCategory(String value, Direction direction, SyntacticCategory returnType,
      SyntacticCategory argumentType, String featureValue, int featureVariable) {
    Preconditions.checkArgument(value != null || 
        (direction != null && returnType != null && argumentType != null));
    this.value = value;
    this.direction = direction;
    this.returnType = returnType;
    this.argumentType = argumentType;
    Preconditions.checkArgument(featureValue.equals(DEFAULT_FEATURE_VALUE) || featureVariable == -1);
    this.featureValue = Preconditions.checkNotNull(featureValue);
    this.featureVariable = featureVariable;

    this.cachedHashCode = 0;
  }

  public static SyntacticCategory createFunctional(Direction direction, SyntacticCategory returnType,
      SyntacticCategory argumentType) {
    return new SyntacticCategory(null, direction, returnType, argumentType, DEFAULT_FEATURE_VALUE, -1);
  }
  
  public static SyntacticCategory createFunctional(Direction direction, SyntacticCategory returnType,
      SyntacticCategory argumentType, String featureValue, int featureVariable) {
    return new SyntacticCategory(null, direction, returnType, argumentType, featureValue, featureVariable);
  }

    public static SyntacticCategory createFunctional(SyntacticCategory returnType, List<Direction> argumentDirections,
						     List<SyntacticCategory> arguments) {
	Preconditions.checkArgument(arguments.size() == argumentDirections.size());
	SyntacticCategory category = returnType;
	for (int i = 0; i < arguments.size(); i++) {
	    category = category.createFunctional(argumentDirections.get(i), category, arguments.get(i));
	}
	return category;
    }
  
  public static SyntacticCategory createAtomic(String value, String featureValue, int featureVariable) {
    return new SyntacticCategory(value, null, null, null, featureValue, featureVariable);
  }

  /**
   * Parses a CCG syntactic type string into a tree.
   * 
   * @param typeString
   * @return
   */
  public static synchronized SyntacticCategory parseFrom(String typeString) {
    return parseSyntacticTypeStringHelper(typeString);
  }

  public static int findSlashIndex(String typeString) {
    int index = 0;
    int parenDepth = 0;

    int minParenDepth = Integer.MAX_VALUE;
    int minParenDepthIndex = -1;

    while (index < typeString.length()) {
      if (typeString.charAt(index) == '\\' || typeString.charAt(index) == '/') {
        if (parenDepth < minParenDepth) {
          minParenDepth = parenDepth;
          minParenDepthIndex = index;
        }
      } else if (typeString.charAt(index) == '(') {
        parenDepth++;
      } else if (typeString.charAt(index) == ')') {
        parenDepth--;
      }
      index++;
    }
    return minParenDepthIndex;
  }

  private static SyntacticCategory parseSyntacticTypeStringHelper(String initialTypeString) {
    String typeString = initialTypeString.trim();
    if (categoryInternmentMap.containsKey(typeString)) {
      return categoryInternmentMap.get(typeString);
    }
    
    int minParenDepthIndex = findSlashIndex(typeString);

    // Features, if present, are always at the end of the string.
    int nextFeatureIndex = typeString.lastIndexOf("[");
    int nextCloseFeatureIndex = typeString.lastIndexOf("]");
    String featureValue = DEFAULT_FEATURE_VALUE;
    int featureVariable = -1;
    if (nextFeatureIndex > minParenDepthIndex && minParenDepthIndex == -1) {
      Preconditions.checkState(nextFeatureIndex + 1 < nextCloseFeatureIndex);
      String tempFeatureValue = typeString.substring(nextFeatureIndex + 1, nextCloseFeatureIndex);
      try {
        featureVariable = Integer.parseInt(tempFeatureValue);
      } catch (NumberFormatException e) {
        featureValue = tempFeatureValue;
      }
      
      // Strip the feature structure from the string.
      typeString = typeString.substring(0, nextFeatureIndex).trim();
      minParenDepthIndex = findSlashIndex(typeString);
    }

    SyntacticCategory returnValue;
    if (minParenDepthIndex == -1) {
      // Atomic category.
      // Strip any parentheses around the variable name.
      String baseSyntacticType = typeString.replaceAll("[\\(\\)]", "").intern();
      returnValue = new SyntacticCategory(baseSyntacticType, null, null, null, featureValue, featureVariable);
    } else {
      // Find the string corresponding to the operator.
      int returnTypeIndex = minParenDepthIndex + 1;

      SyntacticCategory left = parseSyntacticTypeStringHelper(
          typeString.substring(0, minParenDepthIndex));

      SyntacticCategory right = parseSyntacticTypeStringHelper(
          typeString.substring(returnTypeIndex, typeString.length()));

      String directionString = typeString.substring(minParenDepthIndex, returnTypeIndex);
      Direction direction = null;
      if (directionString.startsWith("\\")) {
        direction = Direction.LEFT;
      } else if (directionString.startsWith("/")) {
        direction = Direction.RIGHT;
      } else {
        throw new IllegalArgumentException("Invalid argument direction: " + directionString);
      }

      returnValue = new SyntacticCategory(null, direction, left, right, featureValue, featureVariable);
    }

    // categoryInternmentMap.put(typeString, returnValue);
    return returnValue;
  }

  /**
   * Returns true if this category is not a functional category.
   * 
   * @return
   */
  public boolean isAtomic() {
    return value != null;
  }

  /**
   * Returns the atomic syntactic type of this. Returns null if
   * {@code isAtomic() != true}.
   * 
   * @return
   */
  public String getValue() {
    return value;
  }
  
  /**
   * Gets the value of the feature attached to the root node of {@code this}.
   * Returns {@code DEFAULT_FEATURE_VALUE} if no feature value is associated 
   * with the root variable.
   * 
   * @return
   */
  public String getRootFeature() {
    return featureValue;
  }
  
  /**
   * Gets the feature variable attached to the root node of {@code this}.
   * Returns {@code -1} if no feature variable is associated with the root.
   * 
   * @return
   */
  public int getRootFeatureVariable() {
    return featureVariable;
  }

  /**
   * Gets the direction that this category accepts an argument on.
   * 
   * @return
   */
  public Direction getDirection() {
    return direction;
  }

  public boolean acceptsArgumentOn(Direction direction) {
    return direction != null && this.direction == direction;
  }

  /**
   * Gets the syntactic category which accepts {@code argument} in
   * {@code direction} and returns {@code this}.
   * 
   * @param argument
   * @param direction
   * @return
   */
  public SyntacticCategory addArgument(SyntacticCategory argument, Direction direction) {
    return SyntacticCategory.createFunctional(direction, this, argument);
  }
  
  /**
   * Assigns values to or relabels feature variables in this category. 
   * 
   * @param assignedFeatures
   * @param relabeledFeatures
   * @return
   */
  public SyntacticCategory assignFeatures(Map<Integer, String> assignedFeatures,
      Map<Integer, Integer> relabeledFeatures) {
    String newFeatureValue = featureValue;
    int newFeatureVariable = featureVariable;
    if (assignedFeatures.containsKey(featureVariable)) {
      newFeatureValue = assignedFeatures.get(featureVariable);
      newFeatureVariable = -1;
    } else if (relabeledFeatures.containsKey(featureVariable)) {
      newFeatureVariable = relabeledFeatures.get(newFeatureVariable);
    }
    
    if (isAtomic()) {
      return SyntacticCategory.createAtomic(value, newFeatureValue, newFeatureVariable);
    } else {
      SyntacticCategory assignedReturn = returnType.assignFeatures(assignedFeatures, relabeledFeatures);
      SyntacticCategory assignedArgument = argumentType.assignFeatures(assignedFeatures, relabeledFeatures);
      return SyntacticCategory.createFunctional(direction, assignedReturn, assignedArgument, newFeatureValue, newFeatureVariable);
    }
  }
    
  /**
   * Assigns value to all unfilled feature variables.
   *   
   * @param value
   * @return
   */
  public SyntacticCategory assignAllFeatures(String value) {
    Set<Integer> featureVars = Sets.newHashSet();
    getAllFeatureVariables(featureVars);
    
    Map<Integer, String> valueMap = Maps.newHashMap();
    for (Integer var : featureVars) {
      valueMap.put(var, value);
    }
    return assignFeatures(valueMap, Collections.<Integer, Integer>emptyMap());
  }

  
  public SyntacticCategory getCanonicalForm() {
    Map<Integer, Integer> variableRelabeling = Maps.newHashMap();
    return getCanonicalFormHelper(variableRelabeling);
  }

  private SyntacticCategory getCanonicalFormHelper(Map<Integer, Integer> variableRelabeling) {
    SyntacticCategory newArgument = null, newReturn = null;
    if (!isAtomic()) {
      newReturn = returnType.getCanonicalFormHelper(variableRelabeling);
    }
    
    int newFeatureVariable = featureVariable;
    if (featureVariable != -1) {
      if (!variableRelabeling.containsKey(featureVariable)) {
        variableRelabeling.put(featureVariable, variableRelabeling.size());
      }
      newFeatureVariable = variableRelabeling.get(featureVariable);      
    }

    if (isAtomic()) {
      return SyntacticCategory.createAtomic(value, featureValue, newFeatureVariable);
    } else {
      newArgument = argumentType.getCanonicalFormHelper(variableRelabeling);
      return SyntacticCategory.createFunctional(direction, newReturn, newArgument,
          featureValue, newFeatureVariable);
    }    
  }

  /**
   * Gets the number of subcategories of this category. This number is
   * equal to the number of nodes in the binary tree required to
   * represent the category. Specifically, this method returns 1 if
   * this is an atomic category.
   * 
   * @return
   */
  public int getNumSubcategories() {
    if (isAtomic()) {
      return 1;
    } else {
      return 1 + argumentType.getNumSubcategories() + returnType.getNumSubcategories();
    }
  }

  public int getNumArgumentSubcategories() {
    if (isAtomic()) {
      return 0;
    } else {
      return argumentType.getNumSubcategories();
    }
  }

  public int getNumReturnSubcategories() {
    if (isAtomic()) {
      return 0;
    } else {
      return returnType.getNumSubcategories();
    }
  }

  /**
   * Gets the sequence of arguments that this category accepts. Note
   * that the returned arguments themselves may be functional types.
   * 
   * The sequence is returned with the first required argument at the
   * end of the list.
   * 
   * @return
   */
  public List<SyntacticCategory> getArgumentList() {
    if (isAtomic()) {
      return Lists.newArrayList();
    } else {
      List<SyntacticCategory> args = getReturn().getArgumentList();
      args.add(getArgument());
      return args;
    }
  }

    public List<Direction> getArgumentDirectionList() {
	if (isAtomic()) {
	    return Lists.newArrayList();
	} else {
	    List<Direction> args = getReturn().getArgumentDirectionList();
	    args.add(getDirection());
	    return args;
	}
    }

    public SyntacticCategory getFinalReturnCategory() {
	if (isAtomic()) {
	    return this;
	} else {
	    return getReturn().getFinalReturnCategory();
	}
    }

    

  /**
   * If this is a functional category, this gets the type of the
   * return value. Returns {@code null} if this is an atomic category.
   * 
   * @return
   */
  public SyntacticCategory getReturn() {
    return returnType;
  }

  /**
   * If this is a functional category, this gets the expected type of
   * the function's argument. Returns {@code null} if this is an
   * atomic category.
   * 
   * @return
   */
  public SyntacticCategory getArgument() {
    return argumentType;
  }

  /**
   * Returns {@code true} if this category is unifiable with
   * {@code other}. Two categories are unifiable if there exist
   * assignments to the feature variables of each category which make
   * both categories equal.
   * 
   * @param other
   * @return
   */
  public boolean isUnifiableWith(SyntacticCategory other) {
    Map<Integer, String> myAssignedVariables = Maps.newHashMap();
    Map<Integer, String> otherAssignedVariables = Maps.newHashMap();
    Map<Integer, Integer> variableRelabeling = Maps.newHashMap();

    return isUnifiableWith(other, myAssignedVariables, otherAssignedVariables, variableRelabeling); 
  }
  
  public boolean isUnifiableWith(SyntacticCategory other, Map<Integer, String> myAssignedFeatures,
       Map<Integer, String> otherAssignedFeatures, Map<Integer, Integer> relabeledFeatures) {
    // Ensure that any associated features are compatible
    if (featureVariable != -1 && other.featureVariable != -1) {
      // Try relabeling the two variables to the same variable.
      if (myAssignedFeatures.containsKey(featureVariable) || otherAssignedFeatures.containsKey(other.featureVariable) ||
          (relabeledFeatures.containsKey(featureVariable) && relabeledFeatures.get(featureVariable) != other.featureVariable) || 
          (!relabeledFeatures.containsKey(featureVariable) && relabeledFeatures.containsValue(other.featureVariable))) {
        return false;
      }
      relabeledFeatures.put(featureVariable, other.featureVariable);
    } else if (other.featureVariable != -1) {
      if (relabeledFeatures.containsValue(other.featureVariable) || 
          (otherAssignedFeatures.containsKey(other.featureVariable) && 
              !otherAssignedFeatures.get(other.featureVariable).equals(featureValue))) {
        return false;
      }
      otherAssignedFeatures.put(other.featureVariable, featureValue);
    } else if (featureVariable != -1) {
      if (relabeledFeatures.containsKey(featureVariable) ||
          (myAssignedFeatures.containsKey(featureVariable) && 
              !myAssignedFeatures.get(featureVariable).equals(other.featureValue))) {
        return false;
      }
      myAssignedFeatures.put(featureVariable, other.featureValue);
    } else {
      if (!featureValue.equals(other.featureValue)) {
        return false;
      }
    }

    // Unifiable categories must have the same function specification.
    if (isAtomic() != other.isAtomic()) {
      return false;
    } else if (isAtomic()) {
      return value.equals(other.value);
    } else {
      return direction.equals(other.direction) && 
          argumentType.isUnifiableWith(other.argumentType, myAssignedFeatures, otherAssignedFeatures, relabeledFeatures) &&
          returnType.isUnifiableWith(other.returnType, myAssignedFeatures, otherAssignedFeatures, relabeledFeatures);
    }
  }
  
  private void getAllFeatureVariables(Set<Integer> featureVariables) {
    if (featureVariable != -1) {
      featureVariables.add(featureVariable);
    }
    if (!isAtomic()) {
      argumentType.getAllFeatureVariables(featureVariables);
      returnType.getAllFeatureVariables(featureVariables);
    }
  }

  /**
   * Gets all syntactic categories which can be formed by assigning values to the
   * feature variables of this category. Returned categories may not be in 
   * canonical form.
   * 
   * @param featureValues
   * @return
   */
  public Set<SyntacticCategory> getSubcategories(Set<String> featureValues) {
    Set<Integer> featureVariables = Sets.newHashSet();
    getAllFeatureVariables(featureVariables);
    
    Preconditions.checkState(featureVariables.size() <= 2);
    if (featureVariables.size() == 0) {
      return Sets.newHashSet(this);
    } else if (featureVariables.size() == 1) {
      int varNum = Iterables.getOnlyElement(featureVariables);
      Set<SyntacticCategory> subcategories = Sets.newHashSet(this);
      Map<Integer, String> assignment = Maps.newHashMap();
      Map<Integer, Integer> relabeling = Maps.newHashMap();
      for (String value : featureValues) {
        assignment.clear();
        assignment.put(varNum, value);
        subcategories.add(assignFeatures(assignment, relabeling));
      }
      return subcategories;
    } else {
      List<Integer> list = Lists.newArrayList(featureVariables);
      int varNum1 = list.get(0);
      int varNum2 = list.get(1);
      Set<SyntacticCategory> subcategories = Sets.newHashSet(this);
      Map<Integer, String> assignment = Maps.newHashMap();
      Map<Integer, Integer> relabeling = Maps.newHashMap();
      for (String value1 : featureValues) {
        for (String value2 : featureValues) {
          assignment.clear();
          assignment.put(varNum1, value1);
          assignment.put(varNum2, value2);
          subcategories.add(assignFeatures(assignment, relabeling));
        }
        
        assignment.clear();
        assignment.put(varNum1, value1);
        subcategories.add(assignFeatures(assignment, relabeling));
        
        assignment.clear();
        assignment.put(varNum2, value1);
        subcategories.add(assignFeatures(assignment, relabeling));
      }
      return subcategories;
    }
  }

  private int cacheHashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((argumentType == null) ? 0 : argumentType.hashCode());
    result = prime * result + ((direction == null) ? 0 : direction.hashCode());
    result = prime * result
        + ((featureValue == null) ? 0 : featureValue.hashCode());
    result = prime * result + featureVariable;
    result = prime * result
        + ((returnType == null) ? 0 : returnType.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }
  
  @Override
  public int hashCode() {
    if (cachedHashCode == 0) {
      cachedHashCode = cacheHashCode();
    }
    return cachedHashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SyntacticCategory other = (SyntacticCategory) obj;
    if (argumentType == null) {
      if (other.argumentType != null)
        return false;
    } else if (!argumentType.equals(other.argumentType))
      return false;
    if (direction != other.direction)
      return false;
    if (featureValue == null) {
      if (other.featureValue != null)
        return false;
    } else if (!featureValue.equals(other.featureValue))
      return false;
    if (featureVariable != other.featureVariable)
      return false;
    if (returnType == null) {
      if (other.returnType != null)
        return false;
    } else if (!returnType.equals(other.returnType))
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (isAtomic()) {
      sb.append(value);
    } else {
      sb.append("(");
      sb.append(returnType.toString());
      sb.append(direction.toString());
      sb.append(argumentType.toString());
      sb.append(")");
    }
    if (!featureValue.equals(DEFAULT_FEATURE_VALUE)) {
      sb.append("[");
      sb.append(featureValue);
      sb.append("]");
    } else if (featureVariable != -1) {
      sb.append("[");
      sb.append(featureVariable);
      sb.append("]");
    }
    return sb.toString();
  }
}
