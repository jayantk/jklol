package com.jayantkrish.jklol.experiments.p3;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IndexedList;

public class KbEnvironment {
  
  private final String id;
  private final IndexedList<Object> entities;
    
  private final double[][] categoryFeatures;
  private final double[][] relationFeatures;

  public KbEnvironment(String id, IndexedList<Object> entities, double[][] categoryFeatures,
      double[][] relationFeatures) {
    this.id = Preconditions.checkNotNull(id);
    this.entities = Preconditions.checkNotNull(entities);
    
    this.categoryFeatures = Preconditions.checkNotNull(categoryFeatures);
    this.relationFeatures = Preconditions.checkNotNull(relationFeatures);
    Preconditions.checkArgument(categoryFeatures.length == entities.size());
    Preconditions.checkArgument(relationFeatures.length == entities.size() * entities.size());
  }
  
  public String getId() {
    return id;
  }

  public IndexedList<Object> getEntities() {
    return entities;
  }

  public double[][] getCategoryFeatures() {
    return categoryFeatures;
  }

  public double[][] getRelationFeatures() {
    return relationFeatures;
  }
}
