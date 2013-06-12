package com.jayantkrish.jklol.cvsm;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.CsvParser;
import com.jayantkrish.jklol.util.IoUtils;

public class CvsmUtils {

  public static List<CvsmExample> readTrainingData(String filename) {
    List<CvsmExample> examples = Lists.newArrayList();
    CsvParser csv = new CsvParser(',', '"', CsvParser.NULL_ESCAPE);
    ExpressionParser exp = new ExpressionParser();
    List<String> lines = IoUtils.readLines(filename);
    for (String line : lines) {
      if (line.trim().startsWith("#")) {
        continue;
      }

      String[] parts = csv.parseLine(line);
      if (parts.length == 0) {
        continue;
      }

      Expression logicalForm = exp.parseSingleExpression(parts[0]);

      Tensor target = null;
      if (parts.length > 1) {
        String[] stringValues = parts[1].split(",");
        double[] values = new double[stringValues.length];
        for (int i = 0; i < stringValues.length; i++) {
          values[i] = Double.parseDouble(stringValues[i]);
        }

        target = new DenseTensor(new int[] {0}, new int[] {stringValues.length}, values);
      }

      examples.add(new CvsmExample(logicalForm, target, null));
    }
    return examples;
  }

  private CvsmUtils() {
    // Prevent instantiation
  }
}
