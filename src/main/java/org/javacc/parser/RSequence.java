/* Copyright (c) 2006, Sun Microsystems, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.javacc.parser;

import org.javacc.generator.LexerData;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes regular expressions which are sequences of
 * other regular expressions.
 */

public class RSequence extends RegularExpression {

  /**
   * The list of units in this regular expression sequence.  Each
   * list component will narrow to RegularExpression.
   */
  public List<? super Object> units = new ArrayList<>();

  @Override
  public Nfa GenerateNfa(LexerData data, boolean ignoreCase) {
    if (this.units.size() == 1) {
      return ((RegularExpression) this.units.get(0)).GenerateNfa(data, ignoreCase);
    }

    Nfa retVal = new Nfa(data);
    NfaState startState = retVal.start;
    NfaState finalState = retVal.end;
    Nfa temp1;
    Nfa temp2 = null;

    RegularExpression curRE;

    curRE = (RegularExpression) this.units.get(0);
    temp1 = curRE.GenerateNfa(data, ignoreCase);
    startState.AddMove(temp1.start);

    for (int i = 1; i < this.units.size(); i++) {
      curRE = (RegularExpression) this.units.get(i);

      temp2 = curRE.GenerateNfa(data, ignoreCase);
      temp1.end.AddMove(temp2.start);
      temp1 = temp2;
    }

    temp2.end.AddMove(finalState);

    return retVal;
  }

  RSequence() {}

  RSequence(List<? super Object> seq) {
    this.ordinal = Integer.MAX_VALUE;
    this.units = seq;
  }
}
