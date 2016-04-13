package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;

public class KbEnvironment {
  
  private final String id;
  private final DiscreteFactor categoryFeatures;
  private final VariableNumMap categoryEntityVar;
  private final VariableNumMap categoryTruthVar;

  private final DiscreteFactor relationFeatures;
  private final VariableNumMap relationEntityVars;
  private final VariableNumMap relationTruthVar;
  
  private final List<Object> entities;

  public KbEnvironment(String id, DiscreteFactor categoryFeatures, DiscreteFactor relationFeatures) {
    this.id = Preconditions.checkNotNull(id);
    this.categoryFeatures = Preconditions.checkNotNull(categoryFeatures);
    this.categoryEntityVar = categoryFeatures.getVars().getFirstVariables(1);
    this.categoryTruthVar = categoryFeatures.getVars().getFirstVariables(2)
        .removeAll(categoryEntityVar);
    
    this.relationFeatures = Preconditions.checkNotNull(relationFeatures);
    this.relationEntityVars = relationFeatures.getVars().getFirstVariables(2);
    this.relationTruthVar = relationFeatures.getVars().getFirstVariables(3)
        .removeAll(relationEntityVars);

    this.entities = categoryFeatures.getVars().getDiscreteVariables().get(0).getValues();
  }
  
  public String getId() {
    return id;
  }

  public List<Object> getEntities() {
    return entities;
  }

  public VariableNumMap getCategoryVars() {
    return categoryEntityVar.union(categoryTruthVar);
  }
  
  public DiscreteFactor getEmptyCategoryAssignment() {
    /*
    TableFactorBuilder builder = new TableFactorBuilder(getCategoryVars(),
        DenseTensorBuilder.getFactory());
    return builder.build();
    */
    return TableFactor.zero(getCategoryVars());
  }
  
  public DiscreteFactor getCategoryFeatures() {
    return categoryFeatures;
  }
  
  public VariableNumMap getRelationVars() {
    return relationEntityVars.union(relationTruthVar);
  }
  
  public DiscreteFactor getEmptyRelationAssignment() {
    /*
    TableFactorBuilder builder = new TableFactorBuilder(getRelationVars(),
        DenseTensorBuilder.getFactory());
    return builder.build();
    */
    return TableFactor.zero(getRelationVars());
  }
  
  public DiscreteFactor getRelationFeatures() {
    return relationFeatures;
  }
}
