/*
 * Copyright (c) 2001-2022 Territorium Online Srl / TOL GmbH. All Rights Reserved.
 *
 * This file contains Original Code and/or Modifications of Original Code as defined in and that are
 * subject to the Territorium Online License Version 1.0. You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at http://www.tol.info/license/
 * and read it before using this file.
 *
 * The Original Code and all software distributed under the License are distributed on an 'AS IS'
 * basis, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, AND TERRITORIUM ONLINE HEREBY
 * DISCLAIMS ALL SUCH WARRANTIES, INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT. Please see the License for
 * the specific language governing rights and limitations under the License.
 */

package it.smartio.fastcc.semantic;

import it.smartio.fastcc.parser.JavaCCErrors;
import it.smartio.fastcc.parser.Options;

/**
 * The {@link SemanticContext} class.
 */
public class SemanticContext {

  private final Options options;

  public SemanticContext(Options options) {
    this.options = options;
  }

  final boolean hasErrors() {
    return JavaCCErrors.hasError();
  }

  public final int getLookahead() {
    return this.options.getLookahead();
  }

  public final boolean isForceLaCheck() {
    return this.options.getForceLaCheck();
  }

  public final boolean isSanityCheck() {
    return this.options.getSanityCheck();
  }

  public final int getChoiceAmbiguityCheck() {
    return this.options.getChoiceAmbiguityCheck();
  }

  public final int getOtherAmbiguityCheck() {
    return this.options.getOtherAmbiguityCheck();
  }

  final void onSemanticError(Object node, String message) {
    JavaCCErrors.semantic_error(node, message);
  }

  final void onWarning(String message) {
    JavaCCErrors.warning(message);
  }

  final void onWarning(Object node, String message) {
    JavaCCErrors.warning(node, message);
  }
}
