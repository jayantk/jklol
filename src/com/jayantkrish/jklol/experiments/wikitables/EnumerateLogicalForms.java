package com.jayantkrish.jklol.experiments.wikitables;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.cli.AbstractCli;

public class EnumerateLogicalForms extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> tablesDir;
  private OptionSpec<String> environment;
  
  private OptionSpec<String> trainingLossFile;

  public EnumerateLogicalForms() {
    super(CommonOptions.MAP_REDUCE, CommonOptions.STOCHASTIC_GRADIENT);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    tablesDir = parser.accepts("tablesDir").withRequiredArg().ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class).withValuesSeparatedBy(',');
    
    trainingLossFile = parser.accepts("trainingLossFile").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    // TODO Auto-generated method stub
    
  }
}
