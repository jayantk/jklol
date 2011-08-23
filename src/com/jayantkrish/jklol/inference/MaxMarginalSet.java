package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.Assignment;

/**
 * Max marginals computed for 
 * @author jayant
 */
public interface MaxMarginalSet {

  int beamSize();
  
  Assignment getNthBestAssignment(int n); 
}
