// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

/**
 * The object type of entries in the vector "respecs" of class "TokenProduction".
 */

public class RegExprSpec {

  /**
   * The regular expression of this specification.
   */
  public RegularExpression rexp;

  /**
   * The action corresponding to this specification.
   */
  public Action            act;

  /**
   * The next state corresponding to this specification. If no next state has been specified, this
   * field is set to "null".
   */
  public String            nextState;

  /**
   * If the next state specification was explicit in the previous case, then this token is that of
   * the identifier denoting the next state. This is used for location information, etc. in error
   * reporting.
   */
  public Token             nsTok;

}
