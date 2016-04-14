package com.jayantkrish.jklol.experiments.p3;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;

public class KbState {
  private final KbEnvironment env;
  
  private final Map<String, DiscreteFactor> categories;
  private final Map<String, DiscreteFactor> relations;

  public KbState(KbEnvironment env, Map<String, DiscreteFactor> categories,
      Map<String, DiscreteFactor> relations) {
    this.env = Preconditions.checkNotNull(env);
    this.categories = categories;
    this.relations = relations;
  }
  
  public static KbState unassigned(KbEnvironment env) {
    return new KbState(env, Maps.newHashMap(), Maps.newHashMap());
  }

  public KbEnvironment getEnvironment() {
    return env;
  }

  public Object getCategoryValue(String predicate, Object entity) {
    if (!categories.containsKey(predicate)) {
      return ConstantValue.NIL;
    }
    DiscreteFactor f = categories.get(predicate);
    
    double trueVal = f.getUnnormalizedProbability(env.getCategoryVars()
        .outcomeArrayToAssignment(entity, ConstantValue.TRUE));
    double falseVal = f.getUnnormalizedProbability(env.getCategoryVars()
        .outcomeArrayToAssignment(entity, ConstantValue.FALSE));

    if (trueVal > 0) {
      return ConstantValue.TRUE;
    } else if (falseVal > 0) {
      return ConstantValue.FALSE;
    } else {
      return ConstantValue.NIL;
    }
  }
  
  public Object getRelationValue(String predicate, Object entity1, Object entity2) {
    if (!relations.containsKey(predicate)) {
      return ConstantValue.NIL;
    }
    DiscreteFactor f = relations.get(predicate);
    
    double trueVal = f.getUnnormalizedProbability(env.getRelationVars()
        .outcomeArrayToAssignment(entity1, entity2, ConstantValue.TRUE));
    double falseVal = f.getUnnormalizedProbability(env.getRelationVars()
        .outcomeArrayToAssignment(entity1, entity2, ConstantValue.FALSE));

    if (trueVal > 0) {
      return ConstantValue.TRUE;
    } else if (falseVal > 0) {
      return ConstantValue.FALSE;
    } else {
      return ConstantValue.NIL;
    }
  }

  public KbState setCategoryValue(String predicate, Object entity, Object value) {
    Map<String, DiscreteFactor> newCategories = Maps.newHashMap(categories);
    
    DiscreteFactor predicateFactor = newCategories.get(predicate);
    if (predicateFactor == null) {
      predicateFactor = env.getEmptyCategoryAssignment();
    }
    
    VariableNumMap catVars = env.getCategoryVars();
    predicateFactor = predicateFactor.add(TableFactor.pointDistribution(catVars,
        catVars.outcomeArrayToAssignment(entity, value)));
    
    newCategories.put(predicate, predicateFactor);
    
    return new KbState(env, newCategories, relations);
  }
  
  public KbState setRelationValue(String predicate, Object entity1, Object entity2,
      Object value) {
    Map<String, DiscreteFactor> newRelations = Maps.newHashMap(relations);

    DiscreteFactor predicateFactor = newRelations.get(predicate);
    if (predicateFactor == null) {
      predicateFactor = env.getEmptyRelationAssignment();
    }

    VariableNumMap relVars = env.getRelationVars();
    predicateFactor = predicateFactor.add(TableFactor.pointDistribution(relVars,
        relVars.outcomeArrayToAssignment(entity1, entity2, value)));

    newRelations.put(predicate, predicateFactor);

    return new KbState(env, categories, newRelations);
  }
  
  public Collection<String> getCategories() {
    return categories.keySet();
  }
  
  public DiscreteFactor getCategoryAssignment(String predicate) {
    return categories.get(predicate);
  }
  
  public Collection<String> getRelations() {
    return relations.keySet();
  }

  public DiscreteFactor getRelationAssignment(String predicate) {
    return relations.get(predicate);
  }

  @Override
  public String toString() {
    return categories.toString() + " " + relations.toString();
  }
}
