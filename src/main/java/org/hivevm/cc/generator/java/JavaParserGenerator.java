// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.hivevm.cc.generator.ParserData;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.BNFProduction;
import org.hivevm.cc.parser.Choice;
import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.Lookahead;
import org.hivevm.cc.parser.NonTerminal;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.OneOrMore;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.Sequence;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.parser.ZeroOrMore;
import org.hivevm.cc.parser.ZeroOrOne;
import org.hivevm.cc.semantic.Semanticize;
import org.hivevm.cc.source.SourceWriter;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;

/**
 * Implements the {@link ParserGenerator} for the JAVA language.
 */
public class JavaParserGenerator extends ParserGenerator {

  @Override
  protected void generate(ParserData data) throws IOException {
    TemplateOptions options = new TemplateOptions();
    options.set("IS_GENERATED", data.isGenerated());
    options.set("LOOKAHEAD_NEEDED", data.isLookAheadNeeded());

    options.set("jj2Index", data.jj2Index());
    options.set("maskIndex", data.maskIndex());
    options.set("tokenCount", data.getTokenCount());
    options.set("EXTENDS", data.options().getJavaExtends());
    options.set("IMPORTS", data.options().getJavaImports().isEmpty() ? Collections.emptyList()
        : Arrays.asList(data.options().getJavaImports().split(",")));

    options.add("NORMALPRODUCTIONS", data.getProductions()).set("routine", (n, p) -> generatePhase1((BNFProduction) n,
        generatePhase1Expansion(data, n.getExpansion()), p, data.options()));
    options.add("LOOKAHEADS", data.getLoakaheads()).set("routine",
        (e, p) -> generatePhase2(e.getLaExpansion(), p, data.options()));
    options.add("EXPANSIONS", data.getExpansions()).set("routine",
        (e, p) -> generatePhase3Routine(data, e, data.getCount(e), p));

    options.add("JJ2_INDEX", data.jj2Index()).set("offset", i -> (i + 1));
    options.add("TOKEN_MAX_SIZE", ((data.getTokenCount() - 1) / 32) + 1)
        .set("mask",
            i -> data.maskVals().stream().map(v -> "0x" + Integer.toHexString(v[i])).collect(Collectors.joining(", ")))
        .set("la1", i -> (i == 0) ? "" : (32 * i) + " + ");

    SourceWriter writer = new SourceWriter(data.getParserName(), new DigestOptions(data.options(), options));
    writer.writeTemplate("/templates/java/Parser.template");
    saveOutput(writer, data.options().getOutputDirectory());
  }

  /**
   * The phase 1 routines generates their output into String's and dumps these String's once for
   * each method. These String's contain the special characters '\u0001' to indicate a positive
   * indent, and '\u0002' to indicate a negative indent. '\n' is used to indicate a line terminator.
   * The characters '\u0003' and '\u0004' are used to delineate portions of text where '\n's should
   * not be followed by an indentation.
   */
  private void generatePhase1(BNFProduction p, String code, PrintWriter writer, Options options) {
    Token t = p.getReturnTypeTokens().get(0);

    genHeaderMethod(p, t, writer, options);

    writer.print(" {");

    if (options.getDepthLimit() > 0) {
      writer.println("if(++jj_depth > " + options.getDepthLimit() + ") {");
      writer.println("  jj_consume_token(-1);");
      writer.println("  throw new ParseException();");
      writer.println("}");
      writer.println("try {");
    }

    int indentamt = 4;
    if (options.getDebugParser()) {
      writer.println();
      writer.println("    trace_call(\"" + Encoding.escapeUnicode(p.getLhs()) + "\");");
      writer.println("    try {");
      indentamt = 6;
    }

    if (p.getDeclarationTokens().size() != 0) {
      genTokenSetup((p.getDeclarationTokens().get(0)));
      for (Iterator<Token> it = p.getDeclarationTokens().iterator(); it.hasNext();) {
        t = it.next();
        writer.print(getStringToPrint(t));
      }
      writer.print(getTrailingComments(t));
    }

    char ch = ' ';
    char prevChar;
    boolean indentOn = true;
    for (int i = 0; i < code.length(); i++) {
      prevChar = ch;
      ch = code.charAt(i);
      if ((ch == '\n') && (prevChar == '\r')) {
        // do nothing - we've already printed a new line for the '\r'
        // during the previous iteration.
      } else if ((ch == '\n') || (ch == '\r')) {
        if (indentOn) {
          writer.println();
          for (int i0 = 0; i0 < indentamt; i0++) {
            writer.print(" ");
          }
        } else {
          writer.println();
        }
      } else if (ch == '\u0001') {
        indentamt += 2;
      } else if (ch == '\u0002') {
        indentamt -= 2;
      } else if (ch == '\u0003') {
        indentOn = false;
      } else if (ch == '\u0004') {
        indentOn = true;
      } else {
        writer.print(ch);
      }
    }
    writer.println();

    if (p.getDeclarationEndTokens().size() != 0) {
      genTokenSetup((p.getDeclarationEndTokens().get(0)));
      for (Iterator<Token> it = p.getDeclarationEndTokens().iterator(); it.hasNext();) {
        t = it.next();
        writer.print(getStringToPrint(t));
      }
      writer.println();
    }

    if (options.getDebugParser()) {
      writer.println("    } finally {");
      writer.println("      trace_return(\"" + Encoding.escapeUnicode(p.getLhs()) + "\");");
      writer.println("    }");
    }
    if (options.getDepthLimit() > 0) {
      writer.println(" } finally {");
      writer.println("   --jj_depth;");
      writer.println(" }");
    }
    writer.println("}");
    writer.println();
  }

  private String generatePhase1Expansion(ParserData data, Expansion e) {
    String retval = "";
    Token t = null;
    if (e instanceof RegularExpression) {
      RegularExpression e_nrw = (RegularExpression) e;
      retval += "\n";
      if (e_nrw.getLhsTokens().size() != 0) {
        genTokenSetup((e_nrw.getLhsTokens().get(0)));
        for (Iterator<Token> it = e_nrw.getLhsTokens().iterator(); it.hasNext();) {
          t = it.next();
          retval += getStringToPrint(t);
        }
        retval += getTrailingComments(t);
        retval += " = ";
      }
      String tail = e_nrw.getRhsToken() == null ? ");" : ")." + e_nrw.getRhsToken().image + ";";
      if (e_nrw.getLabel().equals("")) {
        Object label = data.getNameOfToken(e_nrw.getOrdinal());
        if (label != null) {
          retval += "jj_consume_token(" + (String) label + tail;
        } else {
          retval += "jj_consume_token(" + e_nrw.getOrdinal() + tail;
        }
      } else {
        retval += "jj_consume_token(" + e_nrw.getLabel() + tail;
      }

    } else if (e instanceof NonTerminal) {
      NonTerminal e_nrw = (NonTerminal) e;
      retval += "\n";
      if (e_nrw.getLhsTokens().size() != 0) {
        genTokenSetup((e_nrw.getLhsTokens().get(0)));
        for (Iterator<Token> it = e_nrw.getLhsTokens().iterator(); it.hasNext();) {
          t = it.next();
          retval += getStringToPrint(t);
        }
        retval += getTrailingComments(t);
        retval += " = ";
      }
      retval += e_nrw.getName() + "(";
      if (e_nrw.getArgumentTokens().size() != 0) {
        genTokenSetup((e_nrw.getArgumentTokens().get(0)));
        for (Iterator<Token> it = e_nrw.getArgumentTokens().iterator(); it.hasNext();) {
          t = it.next();
          retval += getStringToPrint(t);
        }
        retval += getTrailingComments(t);
      }
      retval += ");";
    } else if (e instanceof Action) {
      Action e_nrw = (Action) e;
      retval += "\u0003\n";
      if (!e_nrw.getActionTokens().isEmpty()) {
        genTokenSetup((e_nrw.getActionTokens().get(0)));
        for (Iterator<Token> it = e_nrw.getActionTokens().iterator(); it.hasNext();) {
          t = it.next();
          retval += getStringToPrint(t);
        }
        retval += getTrailingComments(t);
      }
      retval += "\u0004";
    } else if (e instanceof Choice) {
      Choice e_nrw = (Choice) e;
      Lookahead[] conds = data.getLoakaheads(e);
      String[] actions = new String[e_nrw.getChoices().size() + 1];
      actions[e_nrw.getChoices().size()] = "\n" + "jj_consume_token(-1);\nthrow new ParseException();";

      // In previous line, the "throw" never throws an exception since the
      // evaluation of jj_consume_token(-1) causes ParseException to be
      // thrown first.
      Sequence nestedSeq;
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nestedSeq = (Sequence) (e_nrw.getChoices().get(i));
        actions[i] = generatePhase1Expansion(data, nestedSeq);
      }
      retval = genLookaheadChecker(data, conds, actions);
    } else if (e instanceof Sequence) {
      Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      for (int i = 1; i < e_nrw.getUnits().size(); i++) {
        // For C++, since we are not using exceptions, we will protect all the
        // expansion choices with if (!error)
        boolean wrap_in_block = false;
        retval += generatePhase1Expansion(data, (Expansion) e_nrw.getUnits().get(i));
        if (wrap_in_block) {
          retval += "\n}";
        }
      }
    } else if (e instanceof OneOrMore) {
      OneOrMore e_nrw = (OneOrMore) e;
      Expansion nested_e = e_nrw.getExpansion();
      retval += "\n";
      int labelIndex = nextLabelIndex();
      retval += "label_" + labelIndex + ":\n";
      retval += "while (true) {\u0001";
      retval += generatePhase1Expansion(data, nested_e);
      Lookahead[] conds = data.getLoakaheads(e);
      String[] actions = { "\n;", "\nbreak label_" + labelIndex + ";" };
      retval += genLookaheadChecker(data, conds, actions);
      retval += "\u0002\n" + "}";
    } else if (e instanceof ZeroOrMore) {
      ZeroOrMore e_nrw = (ZeroOrMore) e;
      Expansion nested_e = e_nrw.getExpansion();
      retval += "\n";
      int labelIndex = nextLabelIndex();
      retval += "label_" + labelIndex + ":\n";
      retval += "while (true) {\u0001";
      Lookahead[] conds = data.getLoakaheads(e);
      String[] actions = { "\n;", "\nbreak label_" + labelIndex + ";" };
      retval += genLookaheadChecker(data, conds, actions);
      retval += generatePhase1Expansion(data, nested_e);
      retval += "\u0002\n" + "}";
    } else if (e instanceof ZeroOrOne) {
      ZeroOrOne e_nrw = (ZeroOrOne) e;
      Expansion nested_e = e_nrw.getExpansion();
      Lookahead[] conds = data.getLoakaheads(e);
      String[] actions = { generatePhase1Expansion(data, nested_e), "\n;" };
      retval += genLookaheadChecker(data, conds, actions);
    }
    return retval;
  }

  /**
   * This method takes two parameters - an array of Lookahead's "conds", and an array of String's
   * "actions". "actions" contains exactly one element more than "conds". "actions" are Java source
   * code, and "conds" translate to conditions - so lets say "f(conds[i])" is true if the lookahead
   * required by "conds[i]" is indeed the case. This method returns a string corresponding to the
   * Java code for:
   *
   * if (f(conds[0]) actions[0] else if (f(conds[1]) actions[1] . . . else actions[action.length-1]
   *
   * A particular action entry ("actions[i]") can be null, in which case, a noop is generated for
   * that action.
   */
  private String genLookaheadChecker(ParserData data, Lookahead[] conds, String[] actions) {
    // The state variables.
    LookaheadState state = LookaheadState.NOOPENSTM;
    int indentAmt = 0;
    boolean[] casedValues = new boolean[data.getTokenCount()];
    String retval = "";
    Lookahead la = null;
    Token t = null;

    // Iterate over all the conditions.
    int index = 0;
    boolean jj2LA = false;
    while (index < conds.length) {

      la = conds[index];
      jj2LA = false;

      if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
        // This handles the following cases:
        // . If syntactic lookahead is not wanted (and hence explicitly specified
        // as 0).
        // . If it is possible for the lookahead expansion to recognize the empty
        // string - in which case the lookahead trivially passes.
        // . If the lookahead expansion has a JAVACODE production that it directly
        // expands to - in which case the lookahead trivially passes.
        if (la.getActionTokens().size() == 0) {
          // In addition, if there is no semantic lookahead, then the
          // lookahead trivially succeeds. So break the main loop and
          // treat this case as the default last action.
          break;
        } else {
          // This case is when there is only semantic lookahead
          // (without any preceding syntactic lookahead). In this
          // case, an "if" statement is generated.
          switch (state) {
            case NOOPENSTM:
              retval += "\n" + "if (";
              indentAmt++;
              break;
            case OPENIF:
              retval += "\u0002\n" + "} else if (";
              break;
            case OPENSWITCH:
              retval += "\u0002\n" + "default:" + "\u0001";
              if (data.options().getErrorReporting()) {
                retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
              }
              retval += "\n" + "if (";
              indentAmt++;
          }
          genTokenSetup((la.getActionTokens().get(0)));
          for (Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext();) {
            t = it.next();
            retval += getStringToPrint(t);
          }
          retval += getTrailingComments(t);
          retval += ") {\u0001" + actions[index];
          state = LookaheadState.OPENIF;
        }

      } else if ((la.getAmount() == 1) && (la.getActionTokens().size() == 0)) {
        // Special optimal processing when the lookahead is exactly 1, and there
        // is no semantic lookahead.
        boolean[] firstSet = new boolean[data.getTokenCount()];
        for (int i = 0; i < data.getTokenCount(); i++) {
          firstSet[i] = false;
        }

        // jj2LA is set to false at the beginning of the containing "if" statement.
        // It is checked immediately after the end of the same statement to determine
        // if lookaheads are to be performed using calls to the jj2 methods.
        jj2LA = genFirstSet(data, la.getLaExpansion(), firstSet, jj2LA);
        // genFirstSet may find that semantic attributes are appropriate for the next
        // token. In which case, it sets jj2LA to true.
        if (!jj2LA) {

          // This case is if there is no applicable semantic lookahead and the lookahead
          // is one (excluding the earlier cases such as JAVACODE, etc.).
          switch (state) {
            case OPENIF:
              retval += "\u0002\n" + "} else {\u0001";
              //$FALL-THROUGH$ Control flows through to next case.
            case NOOPENSTM:
              retval += "\n" + "switch (";
              if (data.options().getCacheTokens()) {
                retval += "jj_nt.kind";
                retval += ") {\u0001";
              } else {
                retval += "(jj_ntk==-1)?jj_ntk_f():jj_ntk) {\u0001";
              }
              for (int i = 0; i < data.getTokenCount(); i++) {
                casedValues[i] = false;
              }
              indentAmt++;
              // Don't need to do anything if state is OPENSWITCH.
            default:
          }
          for (int i = 0; i < data.getTokenCount(); i++) {
            if (firstSet[i] && !casedValues[i]) {
              casedValues[i] = true;
              retval += "\u0002\ncase ";
              String s = data.getNameOfToken(i);
              if (s == null) {
                retval += i;
              } else {
                retval += s;
              }
              retval += ":\u0001";
            }
          }
          retval += "{";
          retval += actions[index];
          retval += "\nbreak;\n}";
          state = LookaheadState.OPENSWITCH;

        }

      } else {
        // This is the case when lookahead is determined through calls to
        // jj2 methods. The other case is when lookahead is 1, but semantic
        // attributes need to be evaluated. Hence this crazy control structure.

        jj2LA = true;
      }

      if (jj2LA) {
        // In this case lookahead is determined by the jj2 methods.

        switch (state) {
          case NOOPENSTM:
            retval += "\n" + "if (";
            indentAmt++;
            break;
          case OPENIF:
            retval += "\u0002\n" + "} else if (";
            break;
          case OPENSWITCH:
            retval += "\u0002\n" + "default:" + "\u0001";
            if (data.options().getErrorReporting()) {
              retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
            }
            retval += "\n" + "if (";
            indentAmt++;
        }

        String amount = Integer.toString(la.getAmount());
        retval += "jj_2" + la.getLaExpansion().internal_name + "(" + amount + ")";
        if (la.getActionTokens().size() != 0) {
          // In addition, there is also a semantic lookahead. So concatenate
          // the semantic check with the syntactic one.
          retval += " && (";
          genTokenSetup((la.getActionTokens().get(0)));
          for (Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext();) {
            t = it.next();
            retval += getStringToPrint(t);
          }
          retval += getTrailingComments(t);
          retval += ")";
        }
        retval += ") {\u0001" + actions[index];
        state = LookaheadState.OPENIF;
      }

      index++;
    }

    // Generate code for the default case. Note this may not
    // be the last entry of "actions" if any condition can be
    // statically determined to be always "true".

    switch (state) {
      case NOOPENSTM:
        retval += actions[index];
        break;
      case OPENIF:
        retval += "\u0002\n" + "} else {\u0001" + actions[index];
        break;
      case OPENSWITCH:
        retval += "\u0002\n" + "default:" + "\u0001";
        if (data.options().getErrorReporting()) {
          retval += "\njj_la1[" + data.getIndex(la) + "] = jj_gen;";
        }
        retval += actions[index];
    }
    for (int i = 0; i < indentAmt; i++) {
      retval += "\u0002\n}";
    }
    return retval;
  }

  private final void genHeaderMethod(BNFProduction p, Token t, PrintWriter writer, Options options) {
    genTokenSetup(t);
    writer.print(getLeadingComments(t));
    writer.print("  public final ");
    writer.print(getStringForTokenOnly(t));
    for (int i = 1; i < p.getReturnTypeTokens().size(); i++) {
      t = (p.getReturnTypeTokens().get(i));
      writer.print(getStringToPrint(t));
    }
    writer.print(getTrailingComments(t));
    writer.print(" " + p.getLhs() + "(");
    if (p.getParameterListTokens().size() != 0) {
      genTokenSetup((p.getParameterListTokens().get(0)));
      for (Iterator<Token> it = p.getParameterListTokens().iterator(); it.hasNext();) {
        t = it.next();
        writer.print(getStringToPrint(t));
      }
      writer.print(getTrailingComments(t));
    }
    writer.print(") throws ParseException");

    for (List<Token> name : p.getThrowsList()) {
      writer.print(", ");
      for (Iterator<Token> it2 = name.iterator(); it2.hasNext();) {
        t = it2.next();
        writer.print(t.image);
      }
    }
  }

  private void generatePhase2(Expansion e, PrintWriter writer, Options options) {
    writer.println("  private boolean jj_2" + e.internal_name + "(int xla) {");
    writer.println("    jj_la = xla;");
    writer.println("    jj_lastpos = jj_scanpos = token;");

    String ret_suffix = (options.getDepthLimit() > 0) ? " && !jj_depth_error" : "";

    writer.println("    try {");
    writer.println("      return (!jj_3" + e.internal_name + "()" + ret_suffix + ");");
    writer.println("    } catch (LookaheadSuccess ls) {");
    writer.println("      return true;");
    if (options.getErrorReporting()) {
      writer.println("    } finally {");
      writer.println("      jj_save(" + (Integer.parseInt(e.internal_name.substring(1)) - 1) + ", xla);");
    }
    writer.println("    }");
    writer.println("  }");
    writer.println();
  }

  private void generatePhase3Routine(ParserData data, Expansion e, int count, PrintWriter writer) {
    if (e.internal_name.startsWith("jj_scan_token")) {
      return;
    }

    writer.println("  private boolean jj_3" + e.internal_name + "() {");

    if (data.options().getDepthLimit() > 0) {
      writer.println("if(++jj_depth > " + data.options().getDepthLimit() + ") {");
      writer.println("  jj_consume_token(-1);");
      writer.println("  throw new ParseException();");
      writer.println("}");
      writer.println("try {");
    }

    boolean xsp_declared = false;
    Expansion jj3_expansion = null;
    if (data.options().getDebugLookahead() && (e.parent instanceof NormalProduction)) {
      writer.print("    ");
      if (data.options().getErrorReporting()) {
        writer.print("if (!jj_rescan) ");
      }
      writer.println(
          "trace_call(\"" + Encoding.escapeUnicode(((NormalProduction) e.parent).getLhs()) + "(LOOKING AHEAD...)\");");
      jj3_expansion = e;
    }

    buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, e, count, writer);

    writer.println("    " + genReturn(jj3_expansion, false, data.options()));
    if (data.options().getDepthLimit() > 0) {
      writer.println(" } finally {");
      writer.println("   --jj_depth;");
      writer.println(" }");
    }
    writer.println("  }");
    writer.println();
  }

  private boolean buildPhase3RoutineRecursive(ParserData data, Expansion jj3_expansion, boolean xsp_declared,
      Expansion e, int count, PrintWriter writer) {
    if (e.internal_name.startsWith("jj_scan_token")) {
      return xsp_declared;
    }

    if (e instanceof RegularExpression) {
      RegularExpression e_nrw = (RegularExpression) e;
      writer.print("    if (jj_scan_token(");
      if (e_nrw.getLabel().equals("")) {
        Object label = data.getNameOfToken(e_nrw.getOrdinal());
        writer.print((label == null) ? e_nrw.getOrdinal() : data.getParserName() + "Constants." + label);
      } else {
        writer.print(data.getParserName() + "Constants." + e_nrw.getLabel());
      }
      writer.println("))");
      writer.println("      " + genReturn(jj3_expansion, true, data.options()));
    } else if (e instanceof NonTerminal) {
      // All expansions of non-terminals have the "name" fields set. So
      // there's no need to check it below for "e_nrw" and "ntexp". In
      // fact, we rely here on the fact that the "name" fields of both these
      // variables are the same.
      NonTerminal e_nrw = (NonTerminal) e;
      NormalProduction ntprod = data.getProduction(e_nrw.getName());
      Expansion ntexp = ntprod.getExpansion();
      writer.println("    if (" + genjj_3Call(ntexp) + ")");
      writer.println("      " + genReturn(jj3_expansion, true, data.options()));
    } else if (e instanceof Choice) {
      Sequence nested_seq;
      Choice e_nrw = (Choice) e;
      if (e_nrw.getChoices().size() != 1) {
        if (!xsp_declared) {
          xsp_declared = true;
          writer.println("    Token xsp;");
        }
        writer.println("    xsp = jj_scanpos;");
      }

      Token t = null;
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nested_seq = (Sequence) (e_nrw.getChoices().get(i));
        Lookahead la = (Lookahead) (nested_seq.getUnits().get(0));
        if (la.getActionTokens().size() != 0) {
          writer.println("    jj_lookingAhead = true;");
          writer.print("    jj_semLA = ");
          genTokenSetup((la.getActionTokens().get(0)));
          for (Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext();) {
            t = it.next();
            writer.print(getStringToPrint(t));
          }
          writer.print(getTrailingComments(t));
          writer.println(";");
          writer.println("    jj_lookingAhead = false;");
        }
        writer.print("    if (");
        if (la.getActionTokens().size() != 0) {
          writer.print("!jj_semLA || ");
        }
        if (i != (e_nrw.getChoices().size() - 1)) {
          writer.println(genjj_3Call(nested_seq) + ") {");
          writer.println("      jj_scanpos = xsp;");
        } else {
          writer.println(genjj_3Call(nested_seq) + ")");
          writer.println("      " + genReturn(jj3_expansion, true, data.options()));
        }
      }
      for (int i = 1; i < e_nrw.getChoices().size(); i++) {
        writer.println("    }");
      }
    } else if (e instanceof Sequence) {
      Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      int cnt = count;
      for (int i = 1; i < e_nrw.getUnits().size(); i++) {
        Expansion eseq = (Expansion) (e_nrw.getUnits().get(i));
        xsp_declared = buildPhase3RoutineRecursive(data, jj3_expansion, xsp_declared, eseq, cnt, writer);
        cnt -= ParserGenerator.minimumSize(data, eseq);
        if (cnt <= 0) {
          break;
        }
      }
    } else if (e instanceof OneOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        writer.println("    Token xsp;");
      }
      OneOrMore e_nrw = (OneOrMore) e;
      Expansion nested_e = e_nrw.getExpansion();
      writer.println("    if (" + genjj_3Call(nested_e) + ") " + genReturn(jj3_expansion, true, data.options()));
      writer.println("    while (true) {");
      writer.println("      xsp = jj_scanpos;");
      writer.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
      writer.println("    }");
    } else if (e instanceof ZeroOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        writer.println("    Token xsp;");
      }
      ZeroOrMore e_nrw = (ZeroOrMore) e;
      Expansion nested_e = e_nrw.getExpansion();
      writer.println("    while (true) {");
      writer.println("      xsp = jj_scanpos;");
      writer.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
      writer.println("    }");
    } else if (e instanceof ZeroOrOne) {
      if (!xsp_declared) {
        xsp_declared = true;
        writer.println("    Token xsp;");
      }
      ZeroOrOne e_nrw = (ZeroOrOne) e;
      Expansion nested_e = e_nrw.getExpansion();
      writer.println("    xsp = jj_scanpos;");
      writer.println("    if (" + genjj_3Call(nested_e) + ") jj_scanpos = xsp;");
    }
    return xsp_declared;
  }


  private String genReturn(Expansion expansion, boolean value, Options options) {
    String retval = value ? "true" : "false";
    if (options.getDebugLookahead() && (expansion != null)) {
      String tracecode = "trace_return(\"" + Encoding.escapeUnicode(((NormalProduction) expansion.parent).getLhs())
          + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
      if (options.getErrorReporting()) {
        tracecode = "if (!jj_rescan) " + tracecode;
      }
      return "{ " + tracecode + " return " + retval + "; }";
    } else {
      return "return " + retval + ";";
    }
  }

  private String genjj_3Call(Expansion e) {
    return e.internal_name.startsWith("jj_scan_token") ? e.internal_name : "jj_3" + e.internal_name + "()";
  }
}
