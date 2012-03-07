package com.jayantkrish.jklol.models;

/**
 * A weighted relation between two types of objects.
 *   
 * @author jayantk
 */
public interface WeightedRelation {
  
  public double getWeight(Object domainValue, Object rangeValue); 

  public Factor computeRangeMarginal(Factor domainFactor, Factor rangeFactor); 
  
  public Factor computeRangeMaxMarginal(Factor domainFactor, Factor rangeFactor);
  
  public Factor computeDomainMaxMarginal(Factor domainFactor, Factor rangeFactor);
  
  public Factor computeDomainMarginal(Factor domainFactor, Factor rangeFactor);
}
