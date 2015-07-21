package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

/**
 * An annotation representing a collection of supertags for a
 * sentence. Each token in the sentence is assigned a list of
 * possible CCG syntactic categories, each of which has an
 * additional score between 0 and positive infinity.
 *
 * @author jayantk
 *
 */
public class SupertagAnnotation {
  private final List<List<HeadedSyntacticCategory>> supertags;
  private final List<List<Double>> scores;

  public SupertagAnnotation(List<List<HeadedSyntacticCategory>> supertags,
      List<List<Double>> scores) {
    this.supertags = Preconditions.checkNotNull(supertags);
    this.scores = Preconditions.checkNotNull(scores);
    Preconditions.checkArgument(supertags.size() == scores.size());
  }

  public List<List<HeadedSyntacticCategory>> getSupertags() {
    return supertags;
  }

  public List<List<Double>> getScores() {
    return scores;
  }
}
