package org.wirabumi.cam.process;

public enum DepreciationCalcType {
  PE("Percentage"), TI("Time");

  private String name;

  DepreciationCalcType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

}
