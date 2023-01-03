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

package org.javacc.jjdoc;

import org.fastcc.utils.Encoding;
import org.javacc.generator.JavaCCToken;
import org.javacc.parser.Action;
import org.javacc.parser.BNFProduction;
import org.javacc.parser.CharacterRange;
import org.javacc.parser.Choice;
import org.javacc.parser.Expansion;
import org.javacc.parser.JavaCCData;
import org.javacc.parser.Lookahead;
import org.javacc.parser.NonTerminal;
import org.javacc.parser.NormalProduction;
import org.javacc.parser.OneOrMore;
import org.javacc.parser.RCharacterList;
import org.javacc.parser.RChoice;
import org.javacc.parser.REndOfFile;
import org.javacc.parser.RJustName;
import org.javacc.parser.ROneOrMore;
import org.javacc.parser.RRepetitionRange;
import org.javacc.parser.RSequence;
import org.javacc.parser.RStringLiteral;
import org.javacc.parser.RZeroOrMore;
import org.javacc.parser.RZeroOrOne;
import org.javacc.parser.RegExprSpec;
import org.javacc.parser.RegularExpression;
import org.javacc.parser.Sequence;
import org.javacc.parser.SingleCharacter;
import org.javacc.parser.Token;
import org.javacc.parser.TokenProduction;
import org.javacc.parser.TryBlock;
import org.javacc.parser.ZeroOrMore;
import org.javacc.parser.ZeroOrOne;

import java.util.Iterator;

/**
 * The main entry point for JJDoc.
 */
class JJDoc extends JJDocGlobals {

  static void start(JavaCCData javacc) {
    JJDocGlobals.generator = JJDocGlobals.getGenerator();
    JJDocGlobals.generator.documentStart();
    JJDoc.emitTokenProductions(JJDocGlobals.generator, javacc.getTokenProductions());
    JJDoc.emitNormalProductions(JJDocGlobals.generator, javacc.getNormalProductions());
    JJDocGlobals.generator.documentEnd();
  }

  private static Token getPrecedingSpecialToken(Token tok) {
    Token t = tok;
    while (t.specialToken != null) {
      t = t.specialToken;
    }
    return (t != tok) ? t : null;
  }

  private static void emitTopLevelSpecialTokens(Token tok, Generator gen) {
    if (tok == null) {
      // Strange ...
      return;
    }
    tok = JJDoc.getPrecedingSpecialToken(tok);
    String s = "";
    if (tok != null) {
      JavaCCToken.set(tok);
      while (tok != null) {
        s += JavaCCToken.printTokenOnly(tok);
        tok = tok.next;
      }
    }
    if (!s.equals("")) {
      gen.specialTokens(s);
    }
  }

  /*
   * private static boolean toplevelExpansion(Expansion exp) { return exp.parent != null && (
   * (exp.parent instanceof NormalProduction) || (exp.parent instanceof TokenProduction) ); }
   */

  private static void emitTokenProductions(Generator gen, Iterable<TokenProduction> prods) {
    gen.tokensStart();
    // FIXME there are many empty productions here
    for (TokenProduction tp : prods) {
      JJDoc.emitTopLevelSpecialTokens(tp.firstToken, gen);


      gen.handleTokenProduction(tp);

      // if (!token.equals("")) {
      // gen.tokenStart(tp);
      // String token = getStandardTokenProductionText(tp);
      // gen.text(token);
      // gen.tokenEnd(tp);
      // }
    }
    gen.tokensEnd();
  }

  static String getStandardTokenProductionText(TokenProduction tp) {
    String token = "";
    if (tp.isExplicit) {
      if (tp.lexStates == null) {
        token += "<*> ";
      } else {
        token += "<";
        for (int i = 0; i < tp.lexStates.length; ++i) {
          token += tp.lexStates[i];
          if (i < (tp.lexStates.length - 1)) {
            token += ",";
          }
        }
        token += "> ";
      }
      token += TokenProduction.kindImage[tp.kind];
      if (tp.ignoreCase) {
        token += " [IGNORE_CASE]";
      }
      token += " : {\n";
      for (Iterator<RegExprSpec> it2 = tp.respecs.iterator(); it2.hasNext();) {
        RegExprSpec res = it2.next();

        token += JJDoc.emitRE(res.rexp);

        if (res.nsTok != null) {
          token += " : " + res.nsTok.image;
        }

        token += "\n";
        if (it2.hasNext()) {
          token += "| ";
        }
      }
      token += "}\n\n";
    }
    return token;
  }

  private static void emitNormalProductions(Generator gen, Iterable<NormalProduction> prods) {
    gen.nonterminalsStart();
    for (NormalProduction np : prods) {
      JJDoc.emitTopLevelSpecialTokens(np.getFirstToken(), gen);
      if (np instanceof BNFProduction) {
        gen.productionStart(np);
        if (np.getExpansion() instanceof Choice) {
          boolean first = true;
          Choice c = (Choice) np.getExpansion();
          for (Object element : c.getChoices()) {
            Expansion e = (Expansion) (element);
            gen.expansionStart(e, first);
            JJDoc.emitExpansionTree(e, gen);
            gen.expansionEnd(e, first);
            first = false;
          }
        } else {
          gen.expansionStart(np.getExpansion(), true);
          JJDoc.emitExpansionTree(np.getExpansion(), gen);
          gen.expansionEnd(np.getExpansion(), true);
        }
        gen.productionEnd(np);
      }
    }
    gen.nonterminalsEnd();
  }

  private static void emitExpansionTree(Expansion exp, Generator gen) {
    // gen.text("[->" + exp.getClass().getName() + "]");
    if (exp instanceof Action) {
      JJDoc.emitExpansionAction((Action) exp, gen);
    } else if (exp instanceof Choice) {
      JJDoc.emitExpansionChoice((Choice) exp, gen);
    } else if (exp instanceof Lookahead) {
      JJDoc.emitExpansionLookahead((Lookahead) exp, gen);
    } else if (exp instanceof NonTerminal) {
      JJDoc.emitExpansionNonTerminal((NonTerminal) exp, gen);
    } else if (exp instanceof OneOrMore) {
      JJDoc.emitExpansionOneOrMore((OneOrMore) exp, gen);
    } else if (exp instanceof RegularExpression) {
      JJDoc.emitExpansionRegularExpression((RegularExpression) exp, gen);
    } else if (exp instanceof Sequence) {
      JJDoc.emitExpansionSequence((Sequence) exp, gen);
    } else if (exp instanceof TryBlock) {
      JJDoc.emitExpansionTryBlock((TryBlock) exp, gen);
    } else if (exp instanceof ZeroOrMore) {
      JJDoc.emitExpansionZeroOrMore((ZeroOrMore) exp, gen);
    } else if (exp instanceof ZeroOrOne) {
      JJDoc.emitExpansionZeroOrOne((ZeroOrOne) exp, gen);
    } else {
      JJDocGlobals.error("Oops: Unknown expansion type.");
    }
    // gen.text("[<-" + exp.getClass().getName() + "]");
  }

  private static void emitExpansionAction(Action a, Generator gen) {}

  private static void emitExpansionChoice(Choice c, Generator gen) {
    for (Iterator it = c.getChoices().iterator(); it.hasNext();) {
      Expansion e = (Expansion) (it.next());
      JJDoc.emitExpansionTree(e, gen);
      if (it.hasNext()) {
        gen.text(" | ");
      }
    }
  }

  private static void emitExpansionLookahead(Lookahead l, Generator gen) {}

  private static void emitExpansionNonTerminal(NonTerminal nt, Generator gen) {
    gen.nonTerminalStart(nt);
    gen.text(nt.getName());
    gen.nonTerminalEnd(nt);
  }

  private static void emitExpansionOneOrMore(OneOrMore o, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(o.expansion, gen);
    gen.text(" )+");
  }

  private static void emitExpansionRegularExpression(RegularExpression r, Generator gen) {
    String reRendered = JJDoc.emitRE(r);
    if (!reRendered.equals("")) {
      gen.reStart(r);
      gen.text(reRendered);
      gen.reEnd(r);
    }
  }

  private static void emitExpansionSequence(Sequence s, Generator gen) {
    boolean firstUnit = true;
    for (Object unit : s.units) {
      Expansion e = (Expansion) unit;
      if ((e instanceof Lookahead) || (e instanceof Action)) {
        continue;
      }
      if (!firstUnit) {
        gen.text(" ");
      }
      boolean needParens = (e instanceof Choice) || (e instanceof Sequence);
      if (needParens) {
        gen.text("( ");
      }
      JJDoc.emitExpansionTree(e, gen);
      if (needParens) {
        gen.text(" )");
      }
      firstUnit = false;
    }
  }

  private static void emitExpansionTryBlock(TryBlock t, Generator gen) {
    boolean needParens = t.exp instanceof Choice;
    if (needParens) {
      gen.text("( ");
    }
    JJDoc.emitExpansionTree(t.exp, gen);
    if (needParens) {
      gen.text(" )");
    }
  }

  private static void emitExpansionZeroOrMore(ZeroOrMore z, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(z.expansion, gen);
    gen.text(" )*");
  }

  private static void emitExpansionZeroOrOne(ZeroOrOne z, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(z.expansion, gen);
    gen.text(" )?");
  }

  static String emitRE(RegularExpression re) {
    String returnString = "";
    boolean hasLabel = !re.label.equals("");
    boolean justName = re instanceof RJustName;
    boolean eof = re instanceof REndOfFile;
    boolean isString = re instanceof RStringLiteral;
    boolean toplevelRE = (re.tpContext != null);
    boolean needBrackets = justName || eof || hasLabel || (!isString && toplevelRE);
    if (needBrackets) {
      returnString += "<";
      if (!justName) {
        if (re.private_rexp) {
          returnString += "#";
        }
        if (hasLabel) {
          returnString += re.label;
          returnString += ": ";
        }
      }
    }
    if (re instanceof RCharacterList) {
      RCharacterList cl = (RCharacterList) re;
      if (cl.negated_list) {
        returnString += "~";
      }
      returnString += "[";
      for (Iterator it = cl.descriptors.iterator(); it.hasNext();) {
        Object o = it.next();
        if (o instanceof SingleCharacter) {
          returnString += "\"";
          char s[] = { ((SingleCharacter) o).ch };
          returnString += Encoding.escape(new String(s));
          returnString += "\"";
        } else if (o instanceof CharacterRange) {
          returnString += "\"";
          char s[] = { ((CharacterRange) o).getLeft() };
          returnString += Encoding.escape(new String(s));
          returnString += "\"-\"";
          s[0] = ((CharacterRange) o).getRight();
          returnString += Encoding.escape(new String(s));
          returnString += "\"";
        } else {
          JJDocGlobals.error("Oops: unknown character list element type.");
        }
        if (it.hasNext()) {
          returnString += ",";
        }
      }
      returnString += "]";
    } else if (re instanceof RChoice) {
      RChoice c = (RChoice) re;
      for (Iterator it = c.getChoices().iterator(); it.hasNext();) {
        RegularExpression sub = (RegularExpression) (it.next());
        returnString += JJDoc.emitRE(sub);
        if (it.hasNext()) {
          returnString += " | ";
        }
      }
    } else if (re instanceof REndOfFile) {
      returnString += "EOF";
    } else if (re instanceof RJustName) {
      RJustName jn = (RJustName) re;
      returnString += jn.label;
    } else if (re instanceof ROneOrMore) {
      ROneOrMore om = (ROneOrMore) re;
      returnString += "(";
      returnString += JJDoc.emitRE(om.regexpr);
      returnString += ")+";
    } else if (re instanceof RSequence) {
      RSequence s = (RSequence) re;
      for (Iterator it = s.units.iterator(); it.hasNext();) {
        RegularExpression sub = (RegularExpression) (it.next());
        boolean needParens = false;
        if (sub instanceof RChoice) {
          needParens = true;
        }
        if (needParens) {
          returnString += "(";
        }
        returnString += JJDoc.emitRE(sub);
        if (needParens) {
          returnString += ")";
        }
        if (it.hasNext()) {
          returnString += " ";
        }
      }
    } else if (re instanceof RStringLiteral) {
      RStringLiteral sl = (RStringLiteral) re;
      returnString += ("\"" + Encoding.escape(sl.image) + "\"");
    } else if (re instanceof RZeroOrMore) {
      RZeroOrMore zm = (RZeroOrMore) re;
      returnString += "(";
      returnString += JJDoc.emitRE(zm.regexpr);
      returnString += ")*";
    } else if (re instanceof RZeroOrOne) {
      RZeroOrOne zo = (RZeroOrOne) re;
      returnString += "(";
      returnString += JJDoc.emitRE(zo.regexpr);
      returnString += ")?";
    } else if (re instanceof RRepetitionRange) {
      RRepetitionRange zo = (RRepetitionRange) re;
      returnString += "(";
      returnString += JJDoc.emitRE(zo.regexpr);
      returnString += ")";
      returnString += "{";
      if (zo.hasMax) {
        returnString += zo.min;
        returnString += ",";
        returnString += zo.max;
      } else {
        returnString += zo.min;
      }
      returnString += "}";
    } else {
      JJDocGlobals.error("Oops: Unknown regular expression type.");
    }
    if (needBrackets) {
      returnString += ">";
    }
    return returnString;
  }
}
