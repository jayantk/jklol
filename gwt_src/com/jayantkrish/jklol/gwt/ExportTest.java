package com.jayantkrish.jklol.gwt;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.ExportPackage;
import org.timepedia.exporter.client.Exportable;

import com.jayantkrish.jklol.tensor.SparseTensor;

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
}