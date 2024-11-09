// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes lookahead rule for a particular expansion or expansion sequence (See Sequence.java). In
 * case this describes the lookahead rule for a single expansion unit, then a sequence is created
 * with this node as the first element, and the expansion unit as the second and last element.
 */

public class Lookahead extends Expansion {

  /**
   * Contains the list of tokens that make up the semantic lookahead if any. If this node represents
   * a different kind of lookahead (other than semantic lookahead), then this list contains nothing.
   * If this list contains something, then it is the boolean expression that forms the semantic
   * lookahead. In this case, the following fields "amount" and "la_expansion" are ignored.
   */
  private final List<Token> action_tokens = new ArrayList<>();

  /**
   * The lookahead amount. Its default value essentially gives us infinite lookahead.
   */
  private int               amount        = Integer.MAX_VALUE;

  /**
   * The expansion used to determine whether or not to choose the corresponding parse option. This
   * expansion is parsed upto "amount" tokens of lookahead or until a complete match for it is
   * found. Usually, this is the same as the expansion to be parsed.
   */
  private Expansion         la_expansion;

  /**
   * Is set to true if this is an explicit lookahead specification.
   */
  private boolean           isExplicit;

  /**
   * @return the action_tokens
   */
  public List<Token> getActionTokens() {
    return this.action_tokens;
  }

  /**
   * @return the amount
   */
  public int getAmount() {
    return this.amount;
  }

  /**
   * @return the isExplicit
   */
  public boolean isExplicit() {
    return this.isExplicit;
  }

  /**
   * @return the la_expansion
   */
  public Expansion getLaExpansion() {
    return this.la_expansion;
  }

  /**
   * @param amount the amount to set
   */
  public void setAmount(int amount) {
    this.amount = amount;
  }

  /**
   * @param isExplicit the isExplicit to set
   */
  public void setExplicit(boolean isExplicit) {
    this.isExplicit = isExplicit;
  }

  /**
   * @param la_expansion the la_expansion to set
   */
  public void setLaExpansion(Expansion la_expansion) {
    this.la_expansion = la_expansion;
  }
}
