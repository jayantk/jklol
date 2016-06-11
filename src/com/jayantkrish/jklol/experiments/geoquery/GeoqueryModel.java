package com.jayantkrish.jklol.experiments.geoquery;

import java.io.Serializable;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;

public class GeoqueryModel implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private final CcgParser parser;
  private final FeatureVectorGenerator<StringContext> featureGen;
  
  public GeoqueryModel(CcgParser parser, FeatureVectorGenerator<StringContext> featureGen) {
    this.parser = parser;
    this.featureGen = featureGen;
  }

  public CcgParser getParser() {
    return parser;
  }

  public FeatureVectorGenerator<StringContext> getFeatureGen() {
    return featureGen;
  }
}
