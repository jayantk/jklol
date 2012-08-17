package com.jayantkrish.jklol.ccg;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory.DependencyStructure;

public class CcgParse {

  private final CcgCategory head;

  private final List<DependencyStructure> dependencies;

  // Probability represents the probability of the lexical entry if this is a
  // terminal,
  // otherwise it represents the probability of the generated dependencies.
  private final double probability;
  // Total probability of the parse subtree rooted at this node.
  private final double subtreeProbability;

  private final CcgParse left;
  private final CcgParse right;

  /**
   * Use static methods to construct a {@code CcgParse}.
   * 
   * @param head
   * @param left
   * @param right
   */
  private CcgParse(CcgCategory head, List<DependencyStructure> dependencies, double probability,
      CcgParse left, CcgParse right) {
    this.head = Preconditions.checkNotNull(head);
    this.dependencies = Preconditions.checkNotNull(dependencies);

    this.probability = probability;
    
    this.left = left;
    this.right = right;

    // Both left and right must agree on null/non-null.
    Preconditions.checkArgument((left == null) ^ (right == null) == false);
    
    if (left != null) {
      this.subtreeProbability = left.getSubtreeProbability() * right.getSubtreeProbability() * probability;
    } else {
      this.subtreeProbability = probability;
    }

  }

  public static CcgParse forTerminal(CcgCategory head, double lexicalProbability) {
    return new CcgParse(head, Collections.<DependencyStructure> emptyList(), lexicalProbability, null, null);
  }

  public static CcgParse forNonterminal(CcgCategory head, List<DependencyStructure> dependencies,
      double dependencyProbability, CcgParse left, CcgParse right) {
    return new CcgParse(head, dependencies, dependencyProbability, left, right);
  }

  public boolean isTerminal() {
    return left == null && right == null;
  }
  
  public CcgParse getLeft() {
    return left;
  }
  
  public CcgParse getRight() {
    return right;
  }

  /**
   * Gets the probability of the entire subtree of the CCG parse headed at this
   * node.
   * 
   * @return
   */
  public double getSubtreeProbability() {
    return subtreeProbability;
  }

  /**
   * Returns the probability of the dependencies / lexical entries applied at
   * this particular node.
   * 
   * @return
   */
  public double getNodeProbability() {
    return probability;
  }

  /**
   * Returns dependency structures that were filled by the rule application at
   * this node only.
   * 
   * @return
   */
  public List<DependencyStructure> getNodeDependencies() {
    return dependencies;
  }

  /**
   * Gets all dependency structures populated during parsing.
   * 
   * @return
   */
  public List<DependencyStructure> getAllDependencies() {
    List<DependencyStructure> deps = Lists.newArrayList(dependencies);

    if (!isTerminal()) {
      deps.addAll(left.getAllDependencies());
      deps.addAll(right.getAllDependencies());
    }
    return deps;
  }

  @Override
  public String toString() {
    if (left != null && right != null) {
      return "(" + head + ", " + left + ", " + right + ")";
    } else {
      return "(" + head + ")";
    }
  }
}
