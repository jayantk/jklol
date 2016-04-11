package com.jayantkrish.jklol.cli;

import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureStandardizer;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Standardizes a tensor of features, stored in a file and passed in on the command line. Reads a
 * {@code TableFactor}s stored in a CSV file from the command line, and normalizes each feature in
 * it (giving it zero mean and unit variance).
 *
 * <p> Features with zero variance are not renormalized. This prevents (e.g.,) bias features from
 * being assigned NaN weights.
 */
public class StandardizeFeatures {

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    OptionSpec<String> inputFile = parser.accepts("input").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> delimiterOption = parser.accepts("delimiter").withOptionalArg().ofType(String.class).defaultsTo(",");
    OptionSpec<Integer> featureColumn = parser.accepts("featureColumn").withOptionalArg().ofType(Integer.class).defaultsTo(-1);
    OptionSet options = parser.parse(args);

    // Compute VariableNumMaps for each of the variables in the TableFactor.
    String inputFilename = options.valueOf(inputFile);
    String delimiter = options.valueOf(delimiterOption); 
    int numColumns = IoUtils.getNumberOfColumnsInFile(inputFilename, delimiter);
    List<VariableNumMap> variables = Lists.newArrayList();
    for (int i = 0; i < numColumns - 1; i++) {
      Set<String> variableNames = Sets.newHashSet(IoUtils
          .readColumnFromDelimitedFile(inputFilename, i, delimiter));
      
      int variableIndex = i + 1;
      variables.add(VariableNumMap.singleton(variableIndex, String.valueOf(variableIndex), 
              new DiscreteVariable(String.valueOf(variableIndex), variableNames)));
    }

    // Read in the factor from the input file.
    TableFactor factor = TableFactor.fromDelimitedFile(variables, 
        IoUtils.readLines(inputFilename), delimiter, false, SparseTensorBuilder.getFactory());

    // Determine which variable contains the features.
    int columnIndex = options.valueOf(featureColumn);
    if (columnIndex < 0) {
      columnIndex = numColumns + columnIndex;
    }
   
    FeatureStandardizer standardizer = FeatureStandardizer.estimateFrom(factor, columnIndex, Assignment.EMPTY, 1.0);
    DiscreteFactor standardizedFeatures = standardizer.apply(factor);

    System.out.println(standardizedFeatures.toCsv());
  }
}