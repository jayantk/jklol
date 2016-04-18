package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.util.IndexedList;

public class KbState {
  private final KbEnvironment env;
  
  private final IndexedList<String> categoryNames;
  private final IndexedList<String> relationNames;
  
  private final int[][] categories;
  private final int[][] relations;
  
  public static final int UNASSIGNED_INT = 0;
  public static final int TRUE_INT = 1;
  public static final int FALSE_INT = 2;

  private KbState(KbEnvironment env, IndexedList<String> categoryNames,
      IndexedList<String> relationNames, int[][] categories, int[][] relations) {
    this.env = Preconditions.checkNotNull(env);
    
    this.categoryNames = Preconditions.checkNotNull(categoryNames);
    this.relationNames = Preconditions.checkNotNull(relationNames);

    Preconditions.checkState(categories.length == categoryNames.size());
    Preconditions.checkState(relations.length == relationNames.size());
    this.categories = categories;
    this.relations = relations;
  }
  
  public static KbState unassigned(KbEnvironment env, IndexedList<String> categoryNames,
      IndexedList<String> relationNames) {
    int[][] categories = new int[categoryNames.size()][];
    int[][] relations = new int[relationNames.size()][];
    return new KbState(env, categoryNames, relationNames, categories, relations);
  }

  public KbEnvironment getEnvironment() {
    return env;
  }

  public Object getCategoryValue(String predicate, Object entity) {
    int index = categoryNames.getIndex(predicate);
    if (categories[index] == null) {
      return ConstantValue.NIL;
    }
    int entityIndex = env.getEntities().getIndex(entity);
    
    int val = categories[index][entityIndex];
    
    switch (val) {
    case UNASSIGNED_INT:
      return ConstantValue.NIL;
    case TRUE_INT:
      return ConstantValue.TRUE;
    case FALSE_INT:
      return ConstantValue.FALSE;
    }
    
    throw new IllegalStateException("Assignment had an illegal value"
        + Arrays.toString(categories[index]));
  }
  
  public Object getRelationValue(String predicate, Object entity1, Object entity2) {
    int index = relationNames.getIndex(predicate);
    if (relations[index] == null) {
      return ConstantValue.NIL;
    }
    
    int val = relations[index][getEntityPairIndex(entity1, entity2)];
    
    switch (val) {
    case UNASSIGNED_INT:
      return ConstantValue.NIL;
    case TRUE_INT:
      return ConstantValue.TRUE;
    case FALSE_INT:
      return ConstantValue.FALSE;
    }
    
    throw new IllegalStateException("Assignment had an illegal value"
        + Arrays.toString(relations[index]));
  }

  public KbState setCategoryValue(String predicate, Object entity, Object value) {
    Preconditions.checkState(ConstantValue.isBooleanConstant(value), "Illegal value: %s", value);
    
    int[][] newCategories = new int[categories.length][];
    for (int i = 0; i < categories.length; i++) {
      newCategories[i] = categories[i];
    }

    int predicateIndex = categoryNames.getIndex(predicate);
    int[] predicateAssignment = newCategories[predicateIndex];
    if (predicateAssignment == null) {
      predicateAssignment = new int[env.getEntities().size()];
    } else {
      predicateAssignment = Arrays.copyOf(predicateAssignment, predicateAssignment.length);
    }

    int entityIndex = env.getEntities().getIndex(entity);
    int valueInt = -1;
    if (value.equals(ConstantValue.TRUE)) {
      valueInt = TRUE_INT;
    } else {
      valueInt = FALSE_INT;
    }

    predicateAssignment[entityIndex] = valueInt;
    newCategories[predicateIndex] = predicateAssignment;
    
    return new KbState(env, categoryNames, relationNames, newCategories, relations);
  }
  
  public KbState setRelationValue(String predicate, Object entity1, Object entity2,
      Object value) {
    Preconditions.checkState(ConstantValue.isBooleanConstant(value), "Illegal value: %s", value);
    
    int[][] newRelations = new int[relations.length][];
    for (int i = 0; i < relations.length; i++) {
      newRelations[i] = relations[i];
    }

    int predicateIndex = relationNames.getIndex(predicate);
    int[] predicateAssignment = newRelations[predicateIndex];
    if (predicateAssignment == null) {
      predicateAssignment = new int[env.getEntities().size() * env.getEntities().size()];
    } else {
      predicateAssignment = Arrays.copyOf(predicateAssignment, predicateAssignment.length);
    }

    int entityIndex = getEntityPairIndex(entity1, entity2);
    int valueInt = -1;
    if (value.equals(ConstantValue.TRUE)) {
      valueInt = TRUE_INT;
    } else {
      valueInt = FALSE_INT;
    }

    predicateAssignment[entityIndex] = valueInt;
    newRelations[predicateIndex] = predicateAssignment;
    
    return new KbState(env, categoryNames, relationNames, categories, newRelations);
  }
  
  public IndexedList<String> getCategories() {
    return categoryNames;
  }
  
  public int[][] getCategoryAssignment() {
    return categories;
  }
  
  public IndexedList<String> getRelations() {
    return relationNames;
  }
  
  public int[][] getRelationAssignment() {
    return relations;
  }

  /**
   * Returns {@code true} if this state is consistent {@code other}.
   * Two states are consistent if every category and relation instance
   * with an assigned truth value has the same truth value in both
   * states.
   * 
   * @param other
   * @return
   */
  public boolean isConsistentWith(KbState other) {
    // Both states need to have the same categories and relation
    // names in the same order.
    Preconditions.checkArgument(other.categories.length == categories.length);
    Preconditions.checkArgument(other.relations.length == relations.length);
    
    int[][] otherCategories = other.categories;
    for (int i = 0; i < categories.length; i++) {
      int[] c1 = categories[i];
      int[] c2 = otherCategories[i];
      if (c1 != null && c2 != null) {
        for (int j = 0; j < c1.length; j++) {
          if (c1[j] != c2[j] && c1[j] != UNASSIGNED_INT && c2[j] != UNASSIGNED_INT) {
            return false;
          }
        }
      }
    }

    int[][] otherRelations = other.relations;
    for (int i = 0; i < relations.length; i++) {
      int[] r1 = relations[i];
      int[] r2 = otherRelations[i];
      if (r1 != null && r2 != null) {
        for (int j = 0; j < r1.length; j++) {
          if (r1[j] != r2[j] && r1[j] != UNASSIGNED_INT && r2[j] != UNASSIGNED_INT) {
            return false;
          }
        }
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return categories.toString() + " " + relations.toString();
  }
  
  private final int getEntityPairIndex(Object entity1, Object entity2) {
    int entity1Index = env.getEntities().getIndex(entity1);
    int entity2Index = env.getEntities().getIndex(entity2);
    return entity1Index * env.getEntities().size() + entity2Index;
  }
}
