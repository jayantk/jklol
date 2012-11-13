package com.jayantkrish.jklol.gwt;

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.Exportable;

import com.jayantkrish.jklol.tensor.SparseTensor;

@Export
public class ExportTest implements Exportable {
  public String foo() {
    return "foo";
  }
  public static String bar(){
    return "bar";
  }

  public String baz() {
    return "baz";
  }
}