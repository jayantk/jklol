package com.jayantkrish.jklol.gwt;

import java.util.Arrays;
import java.util.List;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;
import org.timepedia.exporter.client.Exportable;

import com.jayantkrish.jklol.cli.ModelUtils;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

@Export()
@ExportPackage("jklol")
public class ExportTest implements Exportable {

  private final String var;

  public ExportTest(String var) {
    this.var = var;
  }

  public String foo() {
    return "foo";
  }
  public static String bar(){
    return "bar";
  }

  public String getVar() {
    return var;
  }

  public static String testSequenceModel(String input) {
    List<String> emissionFeatures = Arrays.asList("the,DT,the=DT,1",
        "the,N,the=N,0.5", "thing,DT,thing=DT,0.5", "thing,N,thing=N,1");

    ParametricFactorGraph pfg = ModelUtils.buildSequenceModel(emissionFeatures, ","); 
    SufficientStatistics stats = pfg.getNewSufficientStatistics();
    stats.increment(1);
    DynamicFactorGraph model = pfg.getModelFromParameters(stats);

    List<String> words = Arrays.asList(input.split(" "));
    List<String> labels = ModelUtils.testSequenceModel(words, model);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      sb.append(words.get(i) + "/" + labels.get(i) + " ");
    }

    return sb.toString();
  }
}