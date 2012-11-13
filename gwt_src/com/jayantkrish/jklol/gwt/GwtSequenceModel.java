package com.jayantkrish.jklol.gwt;

import com.google.gwt.core.client.EntryPoint;
import org.timepedia.exporter.client.ExporterUtil;

public class GwtSequenceModel implements EntryPoint {

  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    ExporterUtil.exportAll();
  }
}