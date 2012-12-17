package com.jayantkrish.jklol.gwt;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;

public class JklolExporter implements EntryPoint {

  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    GWT.create(ExportTest.class);
  }
}