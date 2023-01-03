/*
 * Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. * Neither the name of the Sun Microsystems, Inc. nor
 * the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package it.smartio.fastcc.semantic;

import java.util.ArrayList;
import java.util.List;

import it.smartio.fastcc.parser.Choice;
import it.smartio.fastcc.parser.Expansion;
import it.smartio.fastcc.parser.Lookahead;
import it.smartio.fastcc.parser.NonTerminal;
import it.smartio.fastcc.parser.NormalProduction;
import it.smartio.fastcc.parser.OneOrMore;
import it.smartio.fastcc.parser.RegularExpression;
import it.smartio.fastcc.parser.Sequence;
import it.smartio.fastcc.parser.TryBlock;
import it.smartio.fastcc.parser.ZeroOrMore;
import it.smartio.fastcc.parser.ZeroOrOne;

abstract class LookaheadWalk {

  private LookaheadWalk() {}

  private static void listAppend(List<MatchInfo> vToAppendTo, List<MatchInfo> vToAppend) {
    for (int i = 0; i < vToAppend.size(); i++) {
      vToAppendTo.add(vToAppend.get(i));
    }
  }

  static List<MatchInfo> genFirstSet(Semanticize data, List<MatchInfo> partialMatches, Expansion exp) {
    if (exp instanceof RegularExpression) {
      List<MatchInfo> retval = new ArrayList<>();
      for (int i = 0; i < partialMatches.size(); i++) {
        MatchInfo m = partialMatches.get(i);
        MatchInfo mnew = new MatchInfo(data.laLimit());
        for (int j = 0; j < m.firstFreeLoc; j++) {
          mnew.match[j] = m.match[j];
        }
        mnew.firstFreeLoc = m.firstFreeLoc;
        mnew.match[mnew.firstFreeLoc++] = ((RegularExpression) exp).ordinal;
        if (mnew.firstFreeLoc == data.laLimit()) {
          data.getSizeLimitedMatches().add(mnew);
        } else {
          retval.add(mnew);
        }
      }
      return retval;
    } else if (exp instanceof NonTerminal) {
      NormalProduction prod = ((NonTerminal) exp).getProd();
      return LookaheadWalk.genFirstSet(data, partialMatches, prod.getExpansion());
    } else if (exp instanceof Choice) {
      List<MatchInfo> retval = new ArrayList<>();
      Choice ch = (Choice) exp;
      for (Object element : ch.getChoices()) {
        List<MatchInfo> v = LookaheadWalk.genFirstSet(data, partialMatches, (Expansion) element);
        LookaheadWalk.listAppend(retval, v);
      }
      return retval;
    } else if (exp instanceof Sequence) {
      List<MatchInfo> v = partialMatches;
      Sequence seq = (Sequence) exp;
      for (Object element : seq.units) {
        v = LookaheadWalk.genFirstSet(data, v, (Expansion) element);
        if (v.size() == 0) {
          break;
        }
      }
      return v;
    } else if (exp instanceof OneOrMore) {
      List<MatchInfo> retval = new ArrayList<>();
      List<MatchInfo> v = partialMatches;
      OneOrMore om = (OneOrMore) exp;
      while (true) {
        v = LookaheadWalk.genFirstSet(data, v, om.expansion);
        if (v.size() == 0) {
          break;
        }
        LookaheadWalk.listAppend(retval, v);
      }
      return retval;
    } else if (exp instanceof ZeroOrMore) {
      List<MatchInfo> retval = new ArrayList<>();
      LookaheadWalk.listAppend(retval, partialMatches);
      List<MatchInfo> v = partialMatches;
      ZeroOrMore zm = (ZeroOrMore) exp;
      while (true) {
        v = LookaheadWalk.genFirstSet(data, v, zm.expansion);
        if (v.size() == 0) {
          break;
        }
        LookaheadWalk.listAppend(retval, v);
      }
      return retval;
    } else if (exp instanceof ZeroOrOne) {
      List<MatchInfo> retval = new ArrayList<>();
      LookaheadWalk.listAppend(retval, partialMatches);
      LookaheadWalk.listAppend(retval, LookaheadWalk.genFirstSet(data, partialMatches, ((ZeroOrOne) exp).expansion));
      return retval;
    } else if (exp instanceof TryBlock) {
      return LookaheadWalk.genFirstSet(data, partialMatches, ((TryBlock) exp).exp);
    } else if (data.considerSemanticLA() && (exp instanceof Lookahead)
        && (((Lookahead) exp).getActionTokens().size() != 0)) {
      return new ArrayList<>();
    } else {
      List<MatchInfo> retval = new ArrayList<>();
      LookaheadWalk.listAppend(retval, partialMatches);
      return retval;
    }
  }

  private static void listSplit(List<MatchInfo> toSplit, List<MatchInfo> mask, List<MatchInfo> partInMask,
      List<MatchInfo> rest) {
    OuterLoop:
    for (int i = 0; i < toSplit.size(); i++) {
      for (int j = 0; j < mask.size(); j++) {
        if (toSplit.get(i) == mask.get(j)) {
          partInMask.add(toSplit.get(i));
          continue OuterLoop;
        }
      }
      rest.add(toSplit.get(i));
    }
  }

  static List<MatchInfo> genFollowSet(List<MatchInfo> partialMatches, Expansion exp, long generation,
      Semanticize data) {
    if (exp.myGeneration == generation) {
      return new ArrayList<>();
    }
    // System.out.println("*** Parent: " + exp.parent);
    exp.myGeneration = generation;
    if (exp.parent == null) {
      List<MatchInfo> retval = new ArrayList<>();
      LookaheadWalk.listAppend(retval, partialMatches);
      return retval;
    } else

    if (exp.parent instanceof NormalProduction) {
      List<Object> parents = ((NormalProduction) exp.parent).getParents();
      List<MatchInfo> retval = new ArrayList<>();
      // System.out.println("1; gen: " + generation + "; exp: " + exp);
      for (Object parent : parents) {
        List<MatchInfo> v = LookaheadWalk.genFollowSet(partialMatches, (Expansion) parent, generation, data);
        LookaheadWalk.listAppend(retval, v);
      }
      return retval;
    } else

    if (exp.parent instanceof Sequence) {
      Sequence seq = (Sequence) exp.parent;
      List<MatchInfo> v = partialMatches;
      for (int i = exp.ordinal + 1; i < seq.units.size(); i++) {
        v = LookaheadWalk.genFirstSet(data, v, (Expansion) seq.units.get(i));
        if (v.size() == 0) {
          return v;
        }
      }
      List<MatchInfo> v1 = new ArrayList<>();
      List<MatchInfo> v2 = new ArrayList<>();
      LookaheadWalk.listSplit(v, partialMatches, v1, v2);
      if (v1.size() != 0) {
        // System.out.println("2; gen: " + generation + "; exp: " + exp);
        v1 = LookaheadWalk.genFollowSet(v1, seq, generation, data);
      }
      if (v2.size() != 0) {
        // System.out.println("3; gen: " + generation + "; exp: " + exp);
        v2 = LookaheadWalk.genFollowSet(v2, seq, data.nextGenerationIndex(), data);
      }
      LookaheadWalk.listAppend(v2, v1);
      return v2;
    } else

    if ((exp.parent instanceof OneOrMore) || (exp.parent instanceof ZeroOrMore)) {
      List<MatchInfo> moreMatches = new ArrayList<>();
      LookaheadWalk.listAppend(moreMatches, partialMatches);
      List<MatchInfo> v = partialMatches;
      while (true) {
        v = LookaheadWalk.genFirstSet(data, v, exp);
        if (v.size() == 0) {
          break;
        }
        LookaheadWalk.listAppend(moreMatches, v);
      }
      List<MatchInfo> v1 = new ArrayList<>();
      List<MatchInfo> v2 = new ArrayList<>();
      LookaheadWalk.listSplit(moreMatches, partialMatches, v1, v2);
      if (v1.size() != 0) {
        // System.out.println("4; gen: " + generation + "; exp: " + exp);
        v1 = LookaheadWalk.genFollowSet(v1, (Expansion) exp.parent, generation, data);
      }
      if (v2.size() != 0) {
        // System.out.println("5; gen: " + generation + "; exp: " + exp);
        v2 = LookaheadWalk.genFollowSet(v2, (Expansion) exp.parent, data.nextGenerationIndex(), data);
      }
      LookaheadWalk.listAppend(v2, v1);
      return v2;
    } else {
      // System.out.println("6; gen: " + generation + "; exp: " + exp);
      return LookaheadWalk.genFollowSet(partialMatches, (Expansion) exp.parent, generation, data);
    }
  }
}
