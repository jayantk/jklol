package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

/**
 * A training example for {@code CcgLoglikelihoodOracle}. Stores an input 
 * word sequence and its expected set of dependencies. 
 * 
 * @author jayant
 */
public class CcgExample {
  
  private final List<String> words;
  private final Set<DependencyStructure> dependencies;
  
  public CcgExample(List<String> words, Set<DependencyStructure> dependencies) {
    this.words = Preconditions.checkNotNull(words);
    this.dependencies = Preconditions.checkNotNull(dependencies);
  }
  
  /**
   * Expected format is (space-separated words)###(#-separated dependency structures).
   *   
   * @param exampleString
   * @return
   */
  public static CcgExample parseFromString(String exampleString) {
    String[] parts = exampleString.split("###");
    List<String> words = Arrays.asList(parts[0].split("\\s+"));
    
    Set<DependencyStructure> dependencies = Sets.newHashSet();
    String[] dependencyParts = parts[1].split("#");
    for (int i = 0; i < dependencyParts.length; i++) {
      String[] dep = dependencyParts[i].split("\\s+");
      dependencies.add(new DependencyStructure(dep[0], Integer.parseInt(dep[1]), dep[3], 
          Integer.parseInt(dep[4]), Integer.parseInt(dep[2])));
    }
    
    return new CcgExample(words, dependencies);
  }

  public List<String> getWords() {
    return words;
  }
  
  public Set<DependencyStructure> getDependencies() {
    return dependencies;
  }
  
  @Override
  public String toString() {
    return words + " " + dependencies;
  }
}
