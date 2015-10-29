package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgRuleSchema;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;

/**
 * Function that creates a {@code ParametricCcgParser} given a CCG
 * lexicon. This class is the equivalent of {@code ParametricCcgParser}
 * when the lexicon of the CCG parser is also being learned.
 * 
 * @author jayantk
 *
 */
public class LexiconInductionCcgParserFactory {

  private final List<CcgBinaryRule> binaryRules;
  private final List<CcgUnaryRule> unaryRules;
  private final CcgFeatureFactory featureFactory;
  private final Set<String> posTagSet;
  private final boolean allowComposition;
  private final Iterable<CcgRuleSchema> allowedCombinationRules;
  private final boolean normalFormOnly;
  
  public LexiconInductionCcgParserFactory(List<CcgBinaryRule> binaryRules,
      List<CcgUnaryRule> unaryRules, CcgFeatureFactory featureFactory, Set<String> posTagSet,
      boolean allowComposition, Iterable<CcgRuleSchema> allowedCombinationRules,
      boolean normalFormOnly) {
    this.binaryRules = binaryRules;
    this.unaryRules = unaryRules;
    this.featureFactory = featureFactory;
    this.posTagSet = posTagSet;
    this.allowComposition = allowComposition;
    this.allowedCombinationRules = allowedCombinationRules;
    this.normalFormOnly = normalFormOnly;
  }

  public ParametricCcgParser getParametricCcgParser(Collection<LexiconEntry> lexicon) {
    return ParametricCcgParser.parseFromLexicon(lexicon, Collections.emptyList(),
        binaryRules, unaryRules, featureFactory, posTagSet, allowComposition,
        allowedCombinationRules, normalFormOnly);
  }
}
