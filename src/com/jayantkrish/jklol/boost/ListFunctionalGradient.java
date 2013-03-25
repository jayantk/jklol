package com.jayantkrish.jklol.boost;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IndexedList;

public class ListFunctionalGradient implements FunctionalGradient {

  private final IndexedList<String> names;
  private final List<FunctionalGradient> gradients;
  
  public ListFunctionalGradient(IndexedList<String> names, List<FunctionalGradient> gradients) {
    this.names = Preconditions.checkNotNull(names);
    this.gradients = Preconditions.checkNotNull(gradients);
        
    Preconditions.checkState(names.size() == gradients.size());
  }
  
  public List<String> getGradientNames() {
    return names.items();
  }

  public List<FunctionalGradient> getGradientList() {
    return gradients;
  }
}
