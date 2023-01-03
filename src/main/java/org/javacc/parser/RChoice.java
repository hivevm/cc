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

import java.util.ArrayList;
import java.util.List;

/**
 * Describes regular expressions which are choices from
 * from among included regular expressions.
 */

public class RChoice extends RegularExpression {

  /**
   * The list of choices of this regular expression.  Each
   * list component will narrow to RegularExpression.
   */
  private List<RegularExpression> choices = new ArrayList<>();

  /**
   * @param choices the choices to set
   */
  public void setChoices(List<RegularExpression> choices) {
    this.choices = choices;
  }

  /**
   * @return the choices
   */
  public List<RegularExpression> getChoices() {
    return this.choices;
  }

  public final void CompressCharLists() {
    CompressChoices(); // Unroll nested choices
    RegularExpression curRE;
    RCharacterList curCharList = null;

    for (int i = 0; i < getChoices().size(); i++) {
      curRE = (RegularExpression) getChoices().get(i);

      while (curRE instanceof RJustName) {
        curRE = ((RJustName) curRE).regexpr;
      }

      if ((curRE instanceof RStringLiteral) && (((RStringLiteral) curRE).image.length() == 1)) {
        getChoices().set(i, curRE = new RCharacterList(((RStringLiteral) curRE).image.charAt(0)));
      }

      if (curRE instanceof RCharacterList) {
        if (((RCharacterList) curRE).negated_list) {
          ((RCharacterList) curRE).RemoveNegation();
        }

        List<Object> tmp = ((RCharacterList) curRE).descriptors;

        if (curCharList == null) {
          getChoices().set(i, curRE = curCharList = new RCharacterList());
        } else {
          getChoices().remove(i--);
        }

        for (int j = tmp.size(); j-- > 0;) {
          curCharList.descriptors.add(tmp.get(j));
        }
      }

    }
  }

  private void CompressChoices() {
    RegularExpression curRE;

    for (int i = 0; i < getChoices().size(); i++) {
      curRE = (RegularExpression) getChoices().get(i);

      while (curRE instanceof RJustName) {
        curRE = ((RJustName) curRE).regexpr;
      }

      if (curRE instanceof RChoice) {
        getChoices().remove(i--);
        for (int j = ((RChoice) curRE).getChoices().size(); j-- > 0;) {
          getChoices().add(((RChoice) curRE).getChoices().get(j));
        }
      }
    }
  }

  @Override
  public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
    return visitor.visit(this, data);
  }
}
