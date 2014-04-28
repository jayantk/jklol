package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public abstract class AbstractParameterSpec implements ParameterSpec {

  private final int id;
  
  private static int idCounter = 0;
  
  public AbstractParameterSpec(int id) {
    this.id = id;
  }

  @Override
  public int getId() {
    return id;
  }

  @Override
  public SufficientStatistics getCurrentParametersByIds(int[] ids) {
    List<SufficientStatistics> stats = Lists.newArrayList();
    List<String> idNames = Lists.newArrayList();
    
    for (int id : ids) {
      ParameterSpec params = getParametersById(id);
      if (params == null) {
        return null;
      } else {
        stats.add(params.getCurrentParameters());
        idNames.add("id:" + id);
      }
    }
    
    if (stats.size() == 1) {
      return stats.get(0);
    } else {
      return new ListSufficientStatistics(idNames, stats);
    }
  }

  public static int getUniqueId() {
    return idCounter++;
  }
}
