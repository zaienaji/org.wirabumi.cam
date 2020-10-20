package org.wirabumi.cam.process;

public enum DepreciationMethod {

  LI("Linear"), CAM_DOUBLEDECLINING("Double Declining");

  private String name;

  DepreciationMethod(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

}
