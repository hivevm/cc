// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.generator.NfaStateData.KindInfo;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;

/**
 * Generate lexer.
 */
class JavaLexerGenerator extends LexerGenerator {

  @Override
  public final void generate(LexerData data) {
    if (JavaCCErrors.hasError()) {
      return;
    }

    TemplateOptions options = new TemplateOptions(data.options());
    options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
        .set("bytes", i -> getLohiBytes(data, i));
    options.add(LexerGenerator.STATES, data.getStateNames()).set("NfaAndDfa",
        (n, w) -> dumpNfaAndDfa(data.getStateData(n), w));
    options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
        .set("NON_ASCII_METHOD", s -> s.nonAsciiMethod)
        .set("ASCII_MOVE", (s, w) -> DumpNonAsciiMoveMethod(s, data, w));

    options.set("LITERAL_IMAGES", () -> JavaLexerGenerator.getStrLiteralImages(data));
    options.set("STATES_FOR_STATE", () -> getStatesForState(data));
    options.set("KIND_FOR_STATE", () -> getKindForState(data));

    options.setWriter("DumpSkipActions", p -> DumpSkipActions(p, data));
    options.setWriter("DumpMoreActions", p -> DumpMoreActions(p, data));
    options.setWriter("DumpTokenActions", p -> DumpTokenActions(p, data));
    options.setWriter("DumpStateSets", p -> DumpStateSets(p, data));
    options.setWriter("DumpGetNextToken", p -> DumpGetNextToken(p, data));
    options.setWriter("dumpStaticVarDeclarations", p -> DumpStaticVarDeclarations(p, data));

    options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
    options.set(LexerGenerator.MAX_LEX_STATES, data.maxLexStates());
    options.set(LexerGenerator.STATE_NAMES, data.getStateNames());
    options.set(LexerGenerator.KEEP_LINE_COL, data.keepLineCol());

    options.set(LexerGenerator.HAS_SKIP, data.hasSkip());
    options.set(LexerGenerator.HAS_MORE, data.hasMore());
    options.set(LexerGenerator.HAS_LOOP, data.hasLoop());
    options.set(LexerGenerator.HAS_SPECIAL, data.hasSpecial());

    options.set(LexerGenerator.HAS_MOPRE_ACTIONS, data.hasMoreActions());
    options.set(LexerGenerator.HAS_SKIP_ACTIONS, data.hasSkipActions());
    options.set(LexerGenerator.HAS_TOKEN_ACTIONS, data.hasTokenActions());
    options.set(LexerGenerator.HAS_EMPTY_MATCH, data.hasEmptyMatch());

    options.set(LexerGenerator.STATE_SET_SIZE, data.stateSetSize());
    options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
    options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());
    options.set(LexerGenerator.STATE_COUNT, data.getStateCount());

    TemplateProvider provider = JavaTemplate.LEXER;
    provider.render(options, data.getParserName());

    generateConstants(data);
  }

  protected final void generateConstants(LexerData data) {
    List<RExpression> expressions = new ArrayList<>();
    for (TokenProduction tp : data.getTokenProductions()) {
      for (RegExprSpec res : tp.getRespecs()) {
        expressions.add(res.rexp);
      }
    }

    TemplateOptions options = new TemplateOptions(data.options());
    options.add("STATES", data.getStateCount()).set("name", data::getStateName);
    options.add("TOKENS", data.getOrderedsTokens()).set("ordinal", RExpression::getOrdinal)
        .set("label", RExpression::getLabel);
    options.add("PRODUCTIONS", expressions).set("label", re -> {
      StringBuilder buffer = new StringBuilder();

      if (re instanceof RStringLiteral) {
        buffer.append("\"\\\"")
            .append(Encoding.escape(Encoding.escape(((RStringLiteral) re).getImage())))
            .append("\\\"\"");
      }
      else if (!re.getLabel().equals("")) {
        buffer.append("\"<").append(re.getLabel()).append(">\"");
      }
      else if (re.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
        JavaCCErrors.warning(re,
            "Consider giving this non-string token a label for better error reporting.");
      }
      else {
        buffer.append("\"<token of kind ").append(re.getOrdinal()).append(">\"");
      }

      if (expressions.indexOf(re) < (expressions.size() - 1)) {
        buffer.append(",");
      }
      return buffer.toString();
    });

    TemplateProvider.render(JavaTemplate.PARSER_CONSTANTS, options, data.getParserName());
  }

  @Override
  protected final Language getLanguage() {
    return Language.JAVA;
  }

  private void DumpStaticVarDeclarations(PrintWriter writer, LexerData data) {
    if (data.maxLexStates() > 1) {
      writer.println();
      writer.println("/** Lex State array. */");
      writer.print("public static final int[] jjnewLexState = {");

      for (int i = 0; i < data.maxOrdinal(); i++) {
        if ((i % 25) == 0) {
          writer.print("\n   ");
        }

        if (data.newLexState(i) == null) {
          writer.print("-1, ");
        }
        else {
          writer.print(data.getStateIndex(data.newLexState(i)) + ", ");
        }
      }
      writer.println("\n};");
    }

    if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
      // Bit vector for TOKEN
      writer.print("static final long[] jjtoToken = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toToken(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasSkip() || data.hasSpecial()) {
      // Bit vector for SKIP
      writer.print("static final long[] jjtoSkip = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toSkip(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasSpecial()) {
      // Bit vector for SPECIAL
      writer.print("static final long[] jjtoSpecial = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toSpecial(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasMore()) {
      // Bit vector for MORE
      writer.print("static final long[] jjtoMore = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toMore(i)) + ", ");
      }
      writer.println("\n};");
    }
  }

  private void DumpGetNextToken(PrintWriter writer, LexerData data) {
    if ((data.getNextStateForEof() != null) || (data.getActionForEof() != null)) {
      writer.println("      TokenLexicalActions(matchedToken);");
    }

    writer.println("      return matchedToken;");
    writer.println("   }");

    if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
      writer.println("   image = jjimage;");
      writer.println("   image.setLength(0);");
      writer.println("   jjimageLen = 0;");
    }

    writer.println("");

    String prefix = "";
    if (data.hasMore()) {
      writer.println("   for (;;)");
      writer.println("   {");
      prefix = "  ";
    }

    String endSwitch = "";
    String caseStr = "";
    // this also sets up the start state of the nfa
    if (data.maxLexStates() > 1) {
      writer.println(prefix + "   switch(curLexState)");
      writer.println(prefix + "   {");
      endSwitch = prefix + "   }";
      caseStr = prefix + "     case ";
      prefix += "    ";
    }

    prefix += "   ";
    for (int i = 0; i < data.maxLexStates(); i++) {
      if (data.maxLexStates() > 1) {
        writer.println(caseStr + i + ":");
      }

      if (data.singlesToSkip(i).HasTransitions()) {
        // added the backup(0) to make JIT happy
        writer.println(prefix + "try { input_stream.backup(0);");
        if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1]
            != 0L)) {
          writer.println(
              prefix + "   while ((curChar < 64" + " && (0x" + Long.toHexString(
                  data.singlesToSkip(i).asciiMoves[0])
                  + "L & (1L << curChar)) != 0L) || \n" + prefix + "          (curChar >> 6) == 1"
                  + " && (0x"
                  + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                  + "L & (1L << (curChar & 077))) != 0L)");
        }
        else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
          writer.println(
              prefix + "   while (curChar <= " + (int) LexerGenerator.MaxChar(
                  data.singlesToSkip(i).asciiMoves[0])
                  + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                  + "L & (1L << curChar)) != 0L)");
        }
        else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
          writer.println(prefix + "   while (curChar > 63 && curChar <= "
              + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && (0x"
              + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
              + "L & (1L << (curChar & 077))) != 0L)");
        }

        if (data.options().getDebugTokenManager()) {
          writer.println(prefix + "{");
          writer.println("      debugStream.println("
              + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
              + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \")\");");
        }
        writer.println(prefix + "      curChar = input_stream.BeginToken();");

        if (data.options().getDebugTokenManager()) {
          writer.println(prefix + "}");
        }

        writer.println(prefix + "}");
        writer.println(prefix + "catch (java.io.IOException e1) { continue EOFLoop; }");
      }

      if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
        if (data.options().getDebugTokenManager()) {
          writer.println(
              "      debugStream.println(\"   Matched the empty string as \" + tokenImage["
                  + data.initMatch(i) + "] + \" token.\");");
        }

        writer.println(prefix + "jjmatchedKind = " + data.initMatch(i) + ";");
        writer.println(prefix + "jjmatchedPos = -1;");
        writer.println(prefix + "curPos = 0;");
      }
      else {
        writer.println(
            prefix + "jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        writer.println(prefix + "jjmatchedPos = 0;");
      }

      if (data.options().getDebugTokenManager()) {
        writer.println("      debugStream.println("
            + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
            + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
            + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
      }

      writer.println(prefix + "curPos = jjMoveStringLiteralDfa0_" + i + "();");
      if (data.canMatchAnyChar(i) != -1) {
        if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
          writer.println(prefix + "if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > "
              + data.canMatchAnyChar(i) + "))");
        }
        else {
          writer.println(
              prefix + "if (jjmatchedPos == 0 && jjmatchedKind > " + data.canMatchAnyChar(i) + ")");
        }
        writer.println(prefix + "{");

        if (data.options().getDebugTokenManager()) {
          writer.println(
              "           debugStream.println(\"   Current character matched as a \" + tokenImage["
                  + data.canMatchAnyChar(i) + "] + \" token.\");");
        }
        writer.println(prefix + "   jjmatchedKind = " + data.canMatchAnyChar(i) + ";");

        if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
          writer.println(prefix + "   jjmatchedPos = 0;");
        }

        writer.println(prefix + "}");
      }

      if (data.maxLexStates() > 1) {
        writer.println(prefix + "break;");
      }
    }

    if (data.maxLexStates() > 1) {
      writer.println(endSwitch);
    }
    else if (data.maxLexStates() == 0) {
      writer.println("       jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    if (data.maxLexStates() > 1) {
      prefix = "  ";
    }
    else {
      prefix = "";
    }

    if (data.maxLexStates() > 0) {
      writer.println(
          prefix + "   if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
      writer.println(prefix + "   {");
      writer.println(prefix + "      if (jjmatchedPos + 1 < curPos)");

      if (data.options().getDebugTokenManager()) {
        writer.println(prefix + "      {");
        writer.println(prefix + "         debugStream.println("
            + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");");
      }

      writer.println(prefix + "         input_stream.backup(curPos - jjmatchedPos - 1);");

      if (data.options().getDebugTokenManager()) {
        writer.println(prefix + "      }");
      }

      if (data.options().getDebugTokenManager()) {
        writer.println("    debugStream.println("
            + "\"****** FOUND A \" + tokenImage[jjmatchedKind] + \" MATCH "
            + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + "
            + "\") ******\\n\");");
      }

      if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
        writer
            .println(prefix + "      if ((jjtoToken[jjmatchedKind >> 6] & "
                + "(1L << (jjmatchedKind & 077))) != 0L)");
        writer.println(prefix + "      {");
      }

      writer.println(prefix + "         matchedToken = jjFillToken();");

      if (data.hasSpecial()) {
        writer.println(prefix + "         matchedToken.specialToken = specialToken;");
      }

      if (data.hasTokenActions()) {
        writer.println(prefix + "         TokenLexicalActions(matchedToken);");
      }

      if (data.maxLexStates() > 1) {
        writer.println("       if (jjnewLexState[jjmatchedKind] != -1)");
        writer.println(prefix + "       curLexState = jjnewLexState[jjmatchedKind];");
      }

      writer.println(prefix + "         return matchedToken;");

      if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
        writer.println(prefix + "      }");

        if (data.hasSkip() || data.hasSpecial()) {
          if (data.hasMore()) {
            writer.println(
                prefix + "      else if ((jjtoSkip[jjmatchedKind >> 6] & "
                    + "(1L << (jjmatchedKind & 077))) != 0L)");
          }
          else {
            writer.println(prefix + "      else");
          }

          writer.println(prefix + "      {");

          if (data.hasSpecial()) {
            writer.println(
                prefix + "         if ((jjtoSpecial[jjmatchedKind >> 6] & "
                    + "(1L << (jjmatchedKind & 077))) != 0L)");
            writer.println(prefix + "         {");

            writer.println(prefix + "            matchedToken = jjFillToken();");

            writer.println(prefix + "            if (specialToken == null)");
            writer.println(prefix + "               specialToken = matchedToken;");
            writer.println(prefix + "            else");
            writer.println(prefix + "            {");
            writer.println(prefix + "               matchedToken.specialToken = specialToken;");
            writer.println(
                prefix + "               specialToken = (specialToken.next = matchedToken);");
            writer.println(prefix + "            }");

            if (data.hasSkipActions()) {
              writer.println(prefix + "            SkipLexicalActions(matchedToken);");
            }

            writer.println(prefix + "         }");

            if (data.hasSkipActions()) {
              writer.println(prefix + "         else");
              writer.println(prefix + "            SkipLexicalActions(null);");
            }
          }
          else if (data.hasSkipActions()) {
            writer.println(prefix + "         SkipLexicalActions(null);");
          }

          if (data.maxLexStates() > 1) {
            writer.println("         if (jjnewLexState[jjmatchedKind] != -1)");
            writer.println(prefix + "         curLexState = jjnewLexState[jjmatchedKind];");
          }

          writer.println(prefix + "         continue EOFLoop;");
          writer.println(prefix + "      }");
        }

        if (data.hasMore()) {
          if (data.hasMoreActions()) {
            writer.println(prefix + "      MoreLexicalActions();");
          }
          else if (data.hasSkipActions() || data.hasTokenActions()) {
            writer.println(prefix + "      jjimageLen += jjmatchedPos + 1;");
          }

          if (data.maxLexStates() > 1) {
            writer.println("      if (jjnewLexState[jjmatchedKind] != -1)");
            writer.println(prefix + "      curLexState = jjnewLexState[jjmatchedKind];");
          }
          writer.println(prefix + "      curPos = 0;");
          writer.println(
              prefix + "      jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

          writer.println(prefix + "      try {");
          writer.println(prefix + "         curChar = input_stream.readChar();");

          if (data.options().getDebugTokenManager()) {
            writer.println("   debugStream.println("
                + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                + "\"Current character : \" + "
                + "TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
          }
          writer.println(prefix + "         continue;");
          writer.println(prefix + "      }");
          writer.println(prefix + "      catch (java.io.IOException e1) { }");
        }
      }

      writer.println(prefix + "   }");
      writer.println(prefix + "   int error_line = input_stream.getEndLine();");
      writer.println(prefix + "   int error_column = input_stream.getEndColumn();");
      writer.println(prefix + "   String error_after = null;");
      writer.println(prefix + "   boolean EOFSeen = false;");
      writer.println(prefix + "   try { input_stream.readChar(); input_stream.backup(1); }");
      writer.println(prefix + "   catch (java.io.IOException e1) {");
      writer.println(prefix + "      EOFSeen = true;");
      writer.println(prefix + "      error_after = curPos <= 1 ? \"\" : input_stream.GetImage();");
      writer.println(prefix + "      if (curChar == '\\n' || curChar == '\\r') {");
      writer.println(prefix + "         error_line++;");
      writer.println(prefix + "         error_column = 0;");
      writer.println(prefix + "      }");
      writer.println(prefix + "      else");
      writer.println(prefix + "         error_column++;");
      writer.println(prefix + "   }");
      writer.println(prefix + "   if (!EOFSeen) {");
      writer.println(prefix + "      input_stream.backup(1);");
      writer.println(prefix + "      error_after = curPos <= 1 ? \"\" : input_stream.GetImage();");
      writer.println(prefix + "   }");
      writer.println(prefix + "   throw new TokenException("
          + "EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenException.LEXICAL_ERROR);");
    }

    if (data.hasMore()) {
      writer.println(prefix + " }");
    }
  }

  private void DumpSkipActions(PrintWriter writer, LexerData data) {
    Outer:
    for (int i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (; ; ) {
        Action act = data.actions(i);
        if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " :");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i)
              + "] == input_stream.getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i)
              + "] == input_stream.getBeginColumn())");
          writer.println("               throw new TokenException("
              + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + input_stream.getBeginLine() + \", "
              + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
          writer.println(
              "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i)
              + "] = input_stream.getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        writer.print("         image.append");
        if (data.getImage(i) != null) {
          writer.println("(jjstrLiteralImages[" + i + "]);");
          writer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
        }
        else {
          writer.println(
              "(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
        }

        genTokenSetup(act.getActionTokens().getFirst());
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
    }
  }

  private void DumpMoreActions(PrintWriter writer, LexerData data) {
    Outer:
    for (int i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toMore(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (; ; ) {
        Action act = data.actions(i);
        if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " :");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i)
              + "] == input_stream.getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i)
              + "] == input_stream.getBeginColumn())");
          writer.println("               throw new TokenException("
              + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + input_stream.getBeginLine() + \", "
              + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
          writer.println(
              "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i)
              + "] = input_stream.getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        writer.print("         image.append");

        if (data.getImage(i) != null) {
          writer.println("(jjstrLiteralImages[" + i + "]);");
        }
        else {
          writer.println("(input_stream.GetSuffix(jjimageLen));");
        }

        writer.println("         jjimageLen = 0;");
        genTokenSetup(act.getActionTokens().getFirst());
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
    }
  }

  private void DumpTokenActions(PrintWriter writer, LexerData data) {
    Action act;
    int i;

    Outer:
    for (i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toToken(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (; ; ) {
        if ((((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) && !data.canLoop(
            data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " :");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i)
              + "] == input_stream.getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i)
              + "] == input_stream.getBeginColumn())");
          writer.println("               throw new TokenException("
              + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
              + "at line \" + input_stream.getBeginLine() + \", "
              + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
          writer.println(
              "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i)
              + "] = input_stream.getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        if (i == 0) {
          writer.println("      image.setLength(0);"); // For EOF no image is there
        }
        else {
          writer.print("        image.append");

          if (data.getImage(i) != null) {
            writer.println("(jjstrLiteralImages[" + i + "]);");
            writer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
          }
          else {
            writer.println(
                "(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
          }
        }

        genTokenSetup(act.getActionTokens().getFirst());
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
    }
  }

  private void DumpStateSets(PrintWriter writer, LexerData data) {
    int cnt = 0;

    writer.print("static final int[] jjnextStates = {");
    if (!data.getOrderedStateSet().isEmpty()) {
      for (int[] set : data.getOrderedStateSet()) {
        for (int element : set) {
          if ((cnt++ % 16) == 0) {
            writer.print("\n   ");
          }

          writer.print(element + ", ");
        }
      }
    }
    else {
      writer.print("0");
    }

    writer.println("\n};");
  }

  private static String getStrLiteralImages(LexerData data) {
    if (data.getImageCount() <= 0) {
      return "";
    }

    String image;
    int i;
    int charCnt = 0; // Set to zero in reInit() but just to be sure

    data.setImage(0, "");

    StringWriter buffer = new StringWriter();
    PrintWriter writer = new PrintWriter(buffer);
    for (i = 0; i < data.getImageCount(); i++) {
      if (((image = data.getImage(i)) == null)
          || (((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) && (
          (data.toMore(i / 64) & (1L << (i % 64))) == 0L)
          && ((data.toToken(i / 64) & (1L << (i % 64))) == 0L))
          || ((data.toSkip(i / 64) & (1L << (i % 64))) != 0L) || (
          (data.toMore(i / 64) & (1L << (i % 64))) != 0L)
          || data.canReachOnMore(data.getState(i))
          || ((data.ignoreCase() || data.ignoreCase(i)) && (
          !image.equals(image.toLowerCase(Locale.ENGLISH))
              || !image.equals(image.toUpperCase(Locale.ENGLISH))))) {
        data.setImage(i, null);
        if ((charCnt += 6) > 80) {
          writer.println("");
          charCnt = 0;
        }

        writer.print("null, ");
        continue;
      }

      String toPrint = "\"";
      for (int j = 0; j < image.length(); j++) {
        if (image.charAt(j) <= 0xff) {
          toPrint += ("\\" + Integer.toOctalString(image.charAt(j)));
        }
        else {
          String hexVal = Integer.toHexString(image.charAt(j));
          if (hexVal.length() == 3) {
            hexVal = "0" + hexVal;
          }
          toPrint += ("\\u" + hexVal);
        }
      }

      toPrint += ("\", ");

      if ((charCnt += toPrint.length()) >= 80) {
        writer.println("");
        charCnt = 0;
      }

      writer.print(toPrint);
    }

    while (++i < data.maxOrdinal()) {
      if ((charCnt += 6) > 80) {
        writer.println("");
        charCnt = 0;
      }

      writer.print("null, ");
    }
    writer.flush();
    return buffer.toString();
  }

  private void DumpStartWithStates(PrintWriter writer, NfaStateData data) {
    writer.println(
        "private int " + "jjStartNfaWithStates" + data.getLexerStateSuffix()
            + "(int pos, int kind, int state)");
    writer.println("{");
    writer.println("   jjmatchedKind = kind;");
    writer.println("   jjmatchedPos = pos;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "   debugStream.println(\"   No more string literal token matches are possible.\");");
      writer.println("   debugStream.println(\"   Currently matched the first \" "
          + "+ (jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
    }

    writer.println("   try { curChar = input_stream.readChar(); }");
    writer.println("   catch(java.io.IOException e) { return pos + 1; }");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("   debugStream.println("
          + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
          + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
          + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }
    writer.println("   return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
    writer.println("}");
  }

  @Override
  protected final void DumpHeadForCase(PrintWriter writer, int byteNum) {
    if (byteNum == 0) {
      writer.println("         long l = 1L << curChar;");
    }
    else if (byteNum == 1) {
      writer.println("         long l = 1L << (curChar & 077);");
    }
    else {
      writer.println("         int hiByte = (curChar >> 8);");
      writer.println("         int i1 = hiByte >> 6;");
      writer.println("         long l1 = 1L << (hiByte & 077);");
      writer.println("         int i2 = (curChar & 0xff) >> 6;");
      writer.println("         long l2 = 1L << (curChar & 077);");
    }

    // writer.println(" MatchLoop: do");
    writer.println("         do");
    writer.println("         {");

    writer.println("            switch(jjstateSet[--i])");
    writer.println("            {");
  }

  private void DumpNonAsciiMoveMethod(NfaState state, LexerData data, PrintWriter writer) {
    for (int j = 0; j < state.loByteVec.size(); j += 2) {
      writer.println("      case " + state.loByteVec.get(j) + ":");
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1)))) {
        writer
            .println("         return ((jjbitVec" + state.loByteVec.get(j + 1) + "[i2"
                + "] & l2) != 0L);");
      }
      else {
        writer.println("            return true;");
      }
    }

    writer.println("      default :");

    for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
        writer.println(
            "         if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)");
      }
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
        writer.println(
            "            if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)");
        writer.println("               return false;");
        writer.println("            else");
      }
      writer.println("            return true;");
    }
  }

  private String getStatesForState(LexerData data) {
    if (data.getStatesForState() == null) {
      assert (false) : "This should never be null.";
      return "null";
    }

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < data.maxLexStates(); i++) {
      if (data.getStatesForState()[i] == null) {
        builder.append("{},");
        continue;
      }
      builder.append("{");
      for (int j = 0; j < data.getStatesForState()[i].length; j++) {
        int[] stateSet = data.getStatesForState()[i][j];
        if (stateSet == null) {
          builder.append("{ ").append(j).append(" },");
          continue;
        }
        builder.append("{ ");
        for (int element : stateSet) {
          builder.append(element).append(",");
        }
        builder.append("},");
      }
      builder.append("},");
    }
    return String.format("{%s}", builder);
  }

  private String getKindForState(LexerData data) {
    if (data.getKinds() == null) {
      return "null";
    }

    StringBuilder builder = new StringBuilder();
    boolean moreThanOne = false;
    for (int[] kind : data.getKinds()) {
      if (moreThanOne) {
        builder.append(",");
      }
      moreThanOne = true;
      if (kind == null) {
        builder.append("{}");
      }
      else {
        builder.append("{ ");
        for (int element : kind) {
          builder.append(element);
          builder.append(",");
        }
        builder.append("}");
      }
    }
    return String.format("{%s}", builder);
  }

  @Override
  protected final void dumpNfaStartStatesCode(PrintWriter writer, NfaStateData data,
      Hashtable<String, long[]>[] statesForPos) {
    if (data.getMaxStrKind() == 0) { // No need to generate this function
      return;
    }

    int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
    boolean condGenerated = false;
    int ind;

    StringBuilder params = new StringBuilder();
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      params.append("long active").append(i).append(", ");
    }
    params.append("long active").append(i).append(")");

    writer.print(
        "private final int jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(int pos, "
            + params);
    writer.println("{");

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "      debugStream.println(\"   No more string literal token matches are possible.\");");
    }

    writer.println("   switch (pos)");
    writer.println("   {");

    for (i = 0; i < (data.getMaxLen() - 1); i++) {
      if (statesForPos[i] == null) {
        continue;
      }

      writer.println("      case " + i + ":");

      Enumeration<String> e = statesForPos[i].keys();
      while (e.hasMoreElements()) {
        String stateSetString = e.nextElement();
        long[] actives = statesForPos[i].get(stateSetString);

        for (int j = 0; j < maxKindsReqd; j++) {
          if (actives[j] == 0L) {
            continue;
          }

          if (condGenerated) {
            writer.print(" || ");
          }
          else {
            writer.print("         if (");
          }

          condGenerated = true;

          writer.print("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
        }

        if (condGenerated) {
          writer.println(")");

          String kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
          String afterKind = stateSetString.substring(ind + 2);
          int jjmatchedPos = Integer.parseInt(afterKind.substring(0, afterKind.indexOf(", ")));

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            writer.println("         {");
          }

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            if (i == 0) {
              writer.println("            jjmatchedKind = " + kindStr + ";");

              if (((data.global.initMatch(data.getStateIndex()) != 0)
                  && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE))) {
                writer.println("            jjmatchedPos = 0;");
              }
            }
            else if (i == jjmatchedPos) {
              if (data.isSubStringAtPos(i)) {
                writer.println("            if (jjmatchedPos != " + i + ")");
                writer.println("            {");
                writer.println("               jjmatchedKind = " + kindStr + ";");
                writer.println("               jjmatchedPos = " + i + ";");
                writer.println("            }");
              }
              else {
                writer.println("            jjmatchedKind = " + kindStr + ";");
                writer.println("            jjmatchedPos = " + i + ";");
              }
            }
            else {
              if (jjmatchedPos > 0) {
                writer.println("            if (jjmatchedPos < " + jjmatchedPos + ")");
              }
              else {
                writer.println("            if (jjmatchedPos == 0)");
              }
              writer.println("            {");
              writer.println("               jjmatchedKind = " + kindStr + ";");
              writer.println("               jjmatchedPos = " + jjmatchedPos + ";");
              writer.println("            }");
            }
          }

          kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
          afterKind = stateSetString.substring(ind + 2);
          stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);

          if (stateSetString.equals("null;")) {
            writer.println("            return -1;");
          }
          else {
            writer.println(
                "            return " + getCompositeStateSet(data, stateSetString) + ";");
          }

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            writer.println("         }");
          }
          condGenerated = false;
        }
      }

      writer.println("         return -1;");
    }

    writer.println("      default :");
    writer.println("         return -1;");
    writer.println("   }");
    writer.println("}");

    params.setLength(0);
    params.append("(int pos, ");
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      params.append("long active").append(i).append(", ");
    }
    params.append("long active").append(i).append(")");

    writer.print("private final int jjStartNfa" + data.getLexerStateSuffix() + params);
    writer.println("{");

    if (data.isMixedState()) {
      if (data.generatedStates() != 0) {
        writer.println(
            "   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data)
                + ", pos + 1);");
      }
      else {
        writer.println("   return pos + 1;");
      }

      writer.println("}");
      return;
    }

    writer.print("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
        + data.getLexerStateSuffix() + "(pos, ");
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      writer.print("active" + i + ", ");
    }
    writer.print("active" + i + ")");
    writer.println(", pos + 1);");
    writer.println("}");
  }

  @Override
  protected final void dumpDfaCode(PrintWriter writer, NfaStateData data) {
    Hashtable<String, ?> tab;
    String key;
    KindInfo info;
    int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
    int i, j, k;
    boolean ifGenerated;

    if (data.getMaxLen() == 0) {
      writer.println(
          "private int " + "jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "()");
      DumpNullStrLiterals(writer, data);
      return;
    }

    if (!data.global.boilerPlateDumped) {
      writer.println("private int " + "jjStopAtPos(int pos, int kind)");
      writer.println("{");
      writer.println("   jjmatchedKind = kind;");
      writer.println("   jjmatchedPos = pos;");

      if (data.global.options().getDebugTokenManager()) {
        writer.println(
            "   debugStream.println(\"   No more string literal token matches are possible.\");");
        writer.println(
            "   debugStream.println(\"   Currently matched the first \" + (jjmatchedPos + 1) + "
                + "\" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
      }

      writer.println("   return pos + 1;");
      writer.println("}");
      data.global.boilerPlateDumped = true;
    }

    for (i = 0; i < data.getMaxLen(); i++) {
      boolean atLeastOne = false;
      boolean startNfaNeeded = false;
      tab = data.getCharPosKind(i);
      String[] keys = LexerGenerator.ReArrange(tab);

      StringBuilder params = new StringBuilder();
      params.append("(");
      if (i != 0) {
        if (i == 1) {
          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= data.getMaxLenForActive(j)) {
              if (atLeastOne) {
                params.append(", ");
              }
              else {
                atLeastOne = true;
              }
              params.append("long active").append(j);
            }
          }

          if (i <= data.getMaxLenForActive(j)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("long active").append(j);
          }
        }
        else {
          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                params.append(", ");
              }
              else {
                atLeastOne = true;
              }
              params.append("long old").append(j).append(", ").append("long active").append(j);
            }
          }

          if (i <= (data.getMaxLenForActive(j) + 1)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("long old").append(j).append(", ").append("long active").append(j);
          }
        }
      }
      params.append(")");
      writer.print(
          "private int " + "jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + params);
      writer.println("{");

      if (i != 0) {
        if (i > 1) {
          atLeastOne = false;
          writer.print("   if ((");

          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                writer.print(" | ");
              }
              else {
                atLeastOne = true;
              }
              writer.print("(active" + j + " &= old" + j + ")");
            }
          }

          if (i <= (data.getMaxLenForActive(j) + 1)) {
            if (atLeastOne) {
              writer.print(" | ");
            }
            writer.print("(active" + j + " &= old" + j + ")");
          }

          writer.println(") == 0L)");
          if (!data.isMixedState() && (data.generatedStates() != 0)) {
            writer.print(
                "      return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if (i <= (data.getMaxLenForActive(j) + 1)) {
                writer.print("old" + j + ", ");
              }
              else {
                writer.print("0L, ");
              }
            }
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              writer.println("old" + j + ");");
            }
            else {
              writer.println("0L);");
            }
          }
          else if (data.generatedStates() != 0) {
            writer.println(
                "      return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data)
                    + ", "
                    + (i - 1) + ");");
          }
          else {
            writer.println("      return " + i + ";");
          }
        }

        if ((i != 0) && data.global.options().getDebugTokenManager()) {
          writer.println(
              "   if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(
                  Integer.MAX_VALUE) + ")");
          writer.println("      debugStream.println(\"   Currently matched the first \" + "
              + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
          writer.println("   debugStream.println(\"   Possible string literal matches : { \"");

          for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
            if (i <= data.getMaxLenForActive(vecs)) {
              writer.println(" +");
              writer.print("         jjKindsForBitVector(" + vecs + ", ");
              writer.print("active" + vecs + ") ");
            }
          }

          writer.println(" + \" } \");");
        }

        writer.println("   try { curChar = input_stream.readChar(); }");
        writer.println("   catch(java.io.IOException e) {");

        if (!data.isMixedState() && (data.generatedStates() != 0)) {
          writer.print(
              "      jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
          for (k = 0; k < (maxLongsReqd - 1); k++) {
            if (i <= data.getMaxLenForActive(k)) {
              writer.print("active" + k + ", ");
            }
            else {
              writer.print("0L, ");
            }
          }

          if (i <= data.getMaxLenForActive(k)) {
            writer.println("active" + k + ");");
          }
          else {
            writer.println("0L);");
          }

          if ((i != 0) && data.global.options().getDebugTokenManager()) {
            writer.println(
                "      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(
                    Integer.MAX_VALUE) + ")");
            writer.println("         debugStream.println(\"   Currently matched the first \" + "
                + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
          }

          writer.println("      return " + i + ";");
        }
        else if (data.generatedStates() != 0) {
          writer.println(
              "   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "
                  + (i - 1) + ");");
        }
        else {
          writer.println("      return " + i + ";");
        }

        writer.println("   }");
      }

      if ((i != 0) && data.global.options().getDebugTokenManager()) {
        writer.println("   debugStream.println("
            + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + "
            : "")
            + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
            + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
      }

      writer.println("   switch(curChar)");
      writer.println("   {");

      CaseLoop:
      for (String key2 : keys) {
        key = key2;
        info = (KindInfo) tab.get(key);
        ifGenerated = false;
        char c = key.charAt(0);

        if ((i == 0) && (c < 128) && info.hasFinalKindCnt()
            && ((data.generatedStates() == 0) || !CanStartNfaUsingAscii(data, c))) {
          for (j = 0; j < maxLongsReqd; j++) {
            if (info.finalKinds[j] != 0L) {
              break;
            }
          }

          int kind;
          for (k = 0; k < 64; k++) {
            if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.isSubString(
                kind = ((j * 64) + k))) {
              if (((data.getIntermediateKinds() != null) && (
                  data.getIntermediateKinds()[((j * 64) + k)] != null)
                  && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                  && (data.getIntermediateMatchedPos() != null)
                  && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i))
                  || ((data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                  && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k)))) {
                break;
              }
              else if (((data.global.toSkip(kind / 64) & (1L << (kind % 64))) != 0L)
                  && ((data.global.toSpecial(kind / 64) & (1L << (kind % 64))) == 0L)
                  && (data.global.actions(kind) == null) && (data.global.newLexState(kind)
                  == null)) {
                continue CaseLoop;
              }
            }
          }
        }

        // Since we know key is a single character ...
        if (data.ignoreCase()) {
          if (c != Character.toUpperCase(c)) {
            writer.println("      case " + (int) Character.toUpperCase(c) + ":");
          }

          if (c != Character.toLowerCase(c)) {
            writer.println("      case " + (int) Character.toLowerCase(c) + ":");
          }
        }

        writer.println("      case " + (int) c + ":");

        long matchedKind;
        String prefix = (i == 0) ? "         " : "            ";

        if (info.hasFinalKindCnt()) {
          for (j = 0; j < maxLongsReqd; j++) {
            if ((matchedKind = info.finalKinds[j]) == 0L) {
              continue;
            }

            for (k = 0; k < 64; k++) {
              if ((matchedKind & (1L << k)) == 0L) {
                continue;
              }

              if (ifGenerated) {
                writer.print("         else if ");
              }
              else if (i != 0) {
                writer.print("         if ");
              }

              ifGenerated = true;

              int kindToPrint;
              if (i != 0) {
                writer.println("((active" + j + " & 0x" + Long.toHexString(1L << k) + "L) != 0L)");
              }

              if ((data.getIntermediateKinds() != null) && (
                  data.getIntermediateKinds()[((j * 64) + k)] != null)
                  && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                  && (data.getIntermediateMatchedPos() != null)
                  && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i)) {
                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                    + "\" cannot be matched as a string literal token " + "at line "
                    + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global,
                    (j * 64) + k)
                    + ". It will be matched as " + GetLabel(data.global,
                    data.getIntermediateKinds()[((j * 64) + k)][i])
                    + ".");
                kindToPrint = data.getIntermediateKinds()[((j * 64) + k)][i];
              }
              else if ((i == 0) && (data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                  && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k))) {
                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                    + "\" cannot be matched as a string literal token " + "at line "
                    + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global,
                    (j * 64) + k)
                    + ". It will be matched as "
                    + GetLabel(data.global, data.global.canMatchAnyChar(data.getStateIndex()))
                    + ".");
                kindToPrint = data.global.canMatchAnyChar(data.getStateIndex());
              }
              else {
                kindToPrint = (j * 64) + k;
              }

              if (!data.isSubString(((j * 64) + k))) {
                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                if (stateSetName != -1) {
                  writer.println(
                      prefix + "return jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i
                          + ", "
                          + kindToPrint + ", " + stateSetName + ");");
                }
                else {
                  writer.println(
                      prefix + "return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");");
                }
              }
              else if (((data.global.initMatch(data.getStateIndex()) != 0)
                  && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE)) || (i
                  != 0)) {
                writer.println("         {");
                writer.println(prefix + "jjmatchedKind = " + kindToPrint + ";");
                writer.println(prefix + "jjmatchedPos = " + i + ";");
                writer.println("         }");
              }
              else {
                writer.println(prefix + "jjmatchedKind = " + kindToPrint + ";");
              }
            }
          }
        }

        if (info.hasValidKindCnt()) {
          atLeastOne = false;

          if (i == 0) {
            writer.print("         return ");

            writer.print("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if ((i + 1) <= data.getMaxLenForActive(j)) {
                if (atLeastOne) {
                  writer.print(", ");
                }
                else {
                  atLeastOne = true;
                }

                writer.print("0x" + Long.toHexString(info.validKinds[j]) + "L");
              }
            }

            if ((i + 1) <= data.getMaxLenForActive(j)) {
              if (atLeastOne) {
                writer.print(", ");
              }

              writer.print("0x" + Long.toHexString(info.validKinds[j]) + "L");
            }
            writer.println(");");
          }
          else {
            writer.print("         return ");

            writer.print("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne) {
                  writer.print(", ");
                }
                else {
                  atLeastOne = true;
                }

                if (info.validKinds[j] != 0L) {
                  writer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]) + "L");
                }
                else {
                  writer.print("active" + j + ", 0L");
                }
              }
            }

            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                writer.print(", ");
              }
              if (info.validKinds[j] != 0L) {
                writer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]) + "L");
              }
              else {
                writer.print("active" + j + ", 0L");
              }
            }

            writer.println(");");
          }
        }
        else // A very special case.
          if ((i == 0) && data.isMixedState()) {

            if (data.generatedStates() != 0) {
              writer.println(
                  "         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(
                      data) + ", 0);");
            }
            else {
              writer.println("         return 1;");
            }
          }
          else if (i != 0) // No more str literals to look for
          {
            writer.println("         break;");
            startNfaNeeded = true;
          }
      }

      writer.println("      default :");

      if (data.global.options().getDebugTokenManager()) {
        writer.println("      debugStream.println(\"   No string literal matches possible.\");");
      }

      if (data.generatedStates() != 0) {
        if (i == 0) {
          /*
           * This means no string literal is possible. Just move nfa with this guy and return.
           */
          writer
              .println(
                  "         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(
                      data) + ", 0);");
        }
        else {
          writer.println("         break;");
          startNfaNeeded = true;
        }
      }
      else {
        writer.println("         return " + (i + 1) + ";");
      }

      writer.println("   }");

      if ((i != 0) && startNfaNeeded) {
        if (!data.isMixedState() && (data.generatedStates() != 0)) {
          /*
           * Here, a string literal is successfully matched and no more string literals are
           * possible. So set the kind and state set upto and including this position for the
           * matched string.
           */

          writer.print("   return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
          for (k = 0; k < (maxLongsReqd - 1); k++) {
            if (i <= data.getMaxLenForActive(k)) {
              writer.print("active" + k + ", ");
            }
            else {
              writer.print("0L, ");
            }
          }
          if (i <= data.getMaxLenForActive(k)) {
            writer.println("active" + k + ");");
          }
          else {
            writer.println("0L);");
          }
        }
        else if (data.generatedStates() != 0) {
          writer.println(
              "   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "
                  + i + ");");
        }
        else {
          writer.println("   return " + (i + 1) + ";");
        }
      }

      writer.println("}");
    }

    if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
      DumpStartWithStates(writer, data);
    }
  }

  @Override
  protected final void dumpMoveNfa(PrintWriter writer, NfaStateData data) {
    writer.println(
        "private int " + "jjMoveNfa" + data.getLexerStateSuffix() + "(int startState, int curPos)");
    writer.println("{");
    if (data.generatedStates() == 0) {
      writer.println("   return curPos;");
      writer.println("}");
      return;
    }

    if (data.isMixedState()) {
      writer.println("   int strKind = jjmatchedKind;");
      writer.println("   int strPos = jjmatchedPos;");
      writer.println("   int seenUpto;");
      writer.println("   input_stream.backup(seenUpto = curPos + 1);");
      writer.println("   try { curChar = input_stream.readChar(); }");
      writer.println("   catch(java.io.IOException e) { throw new Error(\"Internal Error\"); }");
      writer.println("   curPos = 0;");
    }

    writer.println("   int startsAt = 0;");
    writer.println("   jjnewStateCnt = " + data.generatedStates() + ";");
    writer.println("   int i = 1;");
    writer.println("   jjstateSet[0] = startState;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("      debugStream.println(\"   Starting NFA to match one of : \" + "
          + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1));");
    }

    if (data.global.options().getDebugTokenManager()) {
      writer.println("      debugStream.println("
          + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
          + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
          + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    writer.println("   int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    writer.println("   for (;;)");
    writer.println("   {");
    writer.println("      if (++jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    writer.println("         ReInitRounds();");
    writer.println("      if (curChar < 64)");
    writer.println("      {");

    DumpAsciiMoves(writer, data, 0);

    writer.println("      }");

    writer.println("      else if (curChar < 128)");

    writer.println("      {");

    DumpAsciiMoves(writer, data, 1);

    writer.println("      }");

    writer.println("      else");
    writer.println("      {");

    DumpCharAndRangeMoves(writer, data);

    writer.println("      }");

    writer.println("      if (kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    writer.println("      {");
    writer.println("         jjmatchedKind = kind;");
    writer.println("         jjmatchedPos = curPos;");
    writer.println("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    writer.println("      }");
    writer.println("      ++curPos;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(
              Integer.MAX_VALUE) + ")");
      writer.println("         debugStream.println("
          + "\"   Currently matched the first \" + (jjmatchedPos + 1) + \" characters as"
          + " a \" + tokenImage[jjmatchedKind] + \" token.\");");
    }

    writer.println(
        "      if ((i = jjnewStateCnt) == (startsAt = " + data.generatedStates()
            + " - (jjnewStateCnt = startsAt)))");
    if (data.isMixedState()) {
      writer.println("         break;");
    }
    else {
      writer.println("         return curPos;");
    }

    if (data.global.options().getDebugTokenManager()) {
      writer.println("      debugStream.println(\"   Possible kinds of longer matches : \" + "
          + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));");
    }

    writer.println("      try { curChar = input_stream.readChar(); }");
    if (data.isMixedState()) {
      writer.println("      catch(java.io.IOException e) { break; }");
    }
    else {
      writer.println("      catch(java.io.IOException e) { return curPos; }");
    }

    if (data.global.options().getDebugTokenManager()) {
      writer.println("      debugStream.println("
          + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
          + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
          + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    writer.println("   }");

    if (data.isMixedState()) {
      writer.println("   if (jjmatchedPos > strPos)");
      writer.println("      return curPos;");
      writer.println("");
      writer.println("   int toRet = Math.max(curPos, seenUpto);");
      writer.println("");
      writer.println("   if (curPos < toRet)");
      writer.println("      for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )");
      writer.println("         try { curChar = input_stream.readChar(); }");
      writer.println("         catch(java.io.IOException e) { "
          + "throw new Error(\"Internal Error : Please send a bug report.\"); }");
      writer.println("");
      writer.println("   if (jjmatchedPos < strPos)");
      writer.println("   {");
      writer.println("      jjmatchedKind = strKind;");
      writer.println("      jjmatchedPos = strPos;");
      writer.println("   }");
      writer.println("   else if (jjmatchedPos == strPos && jjmatchedKind > strKind)");
      writer.println("      jjmatchedKind = strKind;");
      writer.println("");
      writer.println("   return toRet;");
    }
    writer.println("}");
  }
}
