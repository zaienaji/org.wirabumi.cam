package org.wirabumi.cam.process;

import java.util.ArrayList;
import java.util.List;

public class RuleBuilder {

  private final List<Predicate> predicates = new ArrayList<>();
  private final List<Predicate> negativePredicates = new ArrayList<>();

  private final List<Rule> rules = new ArrayList<>();
  private final List<Rule> negativeRules = new ArrayList<>();

  private RuleBuilder() {

  }

  public static RuleBuilder Create() {
    return new RuleBuilder();
  }

  public static RuleBuilder Create(boolean predicate, String errorMessage) {
    RuleBuilder result = new RuleBuilder();
    return result.and(predicate, errorMessage);
  }

  public static RuleBuilder Create(Rule rule) {
    RuleBuilder result = new RuleBuilder();
    return result.and(rule);
  }

  public RuleBuilder and(boolean predicate, String errorMessage) {
    predicates.add(new Predicate(predicate, errorMessage));

    return this;
  }

  public RuleBuilder andNot(boolean predicate, String errorMessage) {
    negativePredicates.add(new Predicate(predicate, errorMessage));

    return this;
  }

  public RuleBuilder and(Rule rule) {
    rules.add(rule);

    return this;
  }

  public RuleBuilder andNot(Rule rule) {
    negativeRules.add(rule);

    return this;
  }

  public Result evaluate() {

    StringBuilder messages = new StringBuilder();

    for (Predicate predicate : this.predicates) {
      if (!predicate.predicate) {
        messages.append(predicate.errorMessage);
        messages.append(System.lineSeparator());
      }
    }

    for (Predicate negativePredicate : this.negativePredicates) {
      if (negativePredicate.predicate) {
        messages.append(negativePredicate.errorMessage);
        messages.append(System.lineSeparator());
      }
    }

    for (Rule rule : this.rules) {
      Result result = rule.evaluate();
      if (result.isError()) {
        messages.append(result.getErrorMessage());
        messages.append(System.lineSeparator());
      }
    }

    for (Rule negativeRule : this.negativeRules) {
      Result result = negativeRule.evaluate();
      if (!result.isError()) {
        messages.append(result.getErrorMessage());
        messages.append(System.lineSeparator());
      }
    }

    if (messages.length() > 0) {
      return Result.Error(messages.toString());
    }

    return Result.Ok();
  }

  class Predicate {
    boolean predicate;
    String errorMessage;

    public Predicate(boolean predicate, String errorMessage) {
      super();
      this.predicate = predicate;
      this.errorMessage = errorMessage;
    }
  }

}
