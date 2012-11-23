package com.jayantkrish.jklol.gwt;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.RootPanel;

public class JklolExporter implements EntryPoint {

  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    GWT.create(ExportTest.class);
    RootPanel.getBodyElement().setInnerHTML("<p>FoOO</p>");
  }
}