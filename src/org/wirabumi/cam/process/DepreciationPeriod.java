package org.wirabumi.cam.process;

public enum DepreciationPeriod {
  MO("Monthly"), YE("Yearly");

  private String name;

  DepreciationPeriod(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

}
