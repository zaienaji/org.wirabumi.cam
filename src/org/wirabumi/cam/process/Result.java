package org.wirabumi.cam.process;

public class Result {
  private boolean isError;
  private String errorMessage;

  protected Result(boolean isError, String errorMessage) {
    super();
    this.isError = isError;
    this.errorMessage = errorMessage;
  }

  public static Result Ok() {
    return new Result(false, null);
  }

  public static Result Error(String errorMessage) {
    return new Result(true, errorMessage);
  }

  public boolean isError() {
    return isError;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

}
