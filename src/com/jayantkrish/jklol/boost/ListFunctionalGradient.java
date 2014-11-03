package com.jayantkrish.jklol.boost;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Combines multiple independent functional gradients in a list. 
 * This class is useful for problems where multiple regressors
 * each independently optimize some particular portion of an 
 * objective.
 *  
 * @author jayant
 */
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
  
  @Override
  public void combineExamples(FunctionalGradient other) {
    List<FunctionalGradient> otherList = ((ListFunctionalGradient) other).getGradientList(); 
    Preconditions.checkArgument(otherList.size() == gradients.size());
    
    for (int i = 0; i < gradients.size(); i++) {
      gradients.get(i).combineExamples(otherList.get(i));
    }
  }
}
