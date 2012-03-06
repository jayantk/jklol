package com.jayantkrish.jklol.models;

import java.util.List;
 
/**
 * A weighted relation between two domains of objects.
 *   
 * @author jayantk
 */
public interface WeightedRelation {
  
  public double getWeight(Object domainValue, Object rangeValue); 
    
  public Factor apply(List<Object> domainValue);
  
  public Factor invert(List<Object> rangeValue);
    
  public Factor computeRangeMarginal(Factor domainFactor);
  
  public Factor computeRangeMaxMarginal(Factor domainFactor);
  
  public Factor computeDomainMaxMarginal(Factor domainFactor);
  
  public Factor computeDomainMarginal(Factor domainFactor);
    
  public double getPartitionFunction(Factor domainFactor);
  
  public double getMaxPartitionFunction(Factor domainFactor);
}
