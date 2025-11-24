// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.LexerGeneratorRust;
import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.generator.NfaStateData.KindInfo;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.utils.Encoding;
import org.hivevm.core.SourceWriter;
import org.hivevm.core.TemplateLambda;

/**
 * Generate lexer.
 */
class RustLexerGenerator extends LexerGeneratorRust {

    @Override
    public final void generate(LexerData data) {
        if (JavaCCErrors.hasError()) {
            return;
        }

        var options = new TemplateLambda(data.options());
        options.add("LOHI_BYTES_LENGTH", data.getLohiByteSize() + 1);
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
            .set("bytes", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.STATES, data.getStateNames()).set("NfaAndDfa",
                (n, w) -> dumpNfaAndDfa(data.getStateData(n), w));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
                .set("NON_ASCII_METHOD", s -> s.nonAsciiMethod)
                .set("ASCII_MOVE", (s, w) -> DumpNonAsciiMoveMethod(s, data, w));

        options.set("LITERAL_IMAGES", RustLexerGenerator.getStrLiteralImageList(data));
        options.set("LITERAL_IMAGES_LENGTH", RustLexerGenerator.getStrLiteralImageList(data).size());
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
        options.set("STATE_NAMES_LENGTH", data.getStateNames().size());
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
        options.set(LexerGenerator.STATE_SET_SIZE + "2", data.stateSetSize() * 2);
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());

        RustTemplate.LEXER.render(options);

        generateConstants(data);
    }

    protected String self() {
        return "self.";
    }

    protected final void generateConstants(LexerData data) {
        var expressions = new ArrayList<RExpression>();
        for (var  tp : data.getTokenProductions()) {
            for (var  res : tp.getRespecs()) {
                expressions.add(res.rexp);
            }
        }

        var options = new TemplateLambda(data.options());
        options.add("STATES", data.getStateCount()).set("name", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens()).set("ordinal", RExpression::getOrdinal)
            .set("label", RExpression::getLabel);
        options.add("PRODUCTIONS_COUNT", expressions.size() + 1);
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

        RustTemplate.PARSER_CONSTANTS.render(options);
    }

    @Override
    protected final Language getLanguage() {
        return Language.RUST;
    }

    private void DumpStaticVarDeclarations(SourceWriter writer, LexerData data) {
        if (data.maxLexStates() > 1) {
            writer.new_line();
            writer.append("const JJNEW_LEX_STATE : [i8; " + data.maxOrdinal() + "] = [");

            for (int i = 0; i < data.maxOrdinal(); i++) {
                if ((i % 25) == 0) {
                    writer.append("\n   ");
                }

                if (data.newLexState(i) == null) {
                    writer.append("-1, ");
                }
                else {
                    writer.append(data.getStateIndex(data.newLexState(i)) + ", ");
                }
            }
            writer.println("\n];");
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            // Bit vector for TOKEN
            writer.append("const JJTO_TOKEN : [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toToken(i)) + ", ");
            }
            writer.println("\n];");
        }

        if (data.hasSkip() || data.hasSpecial()) {
            // Bit vector for SKIP
            writer.append("const JJTO_SKIP : [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toSkip(i)) + ", ");
            }
            writer.println("\n];");
        }

        if (data.hasSpecial()) {
            // Bit vector for SPECIAL
            writer.append("static final long[] jjtoSpecial = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toSpecial(i)) + ", ");
            }
            writer.println("\n};");
        }

        if (data.hasMore()) {
            // Bit vector for MORE
            writer.append("static final long[] jjtoMore = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toMore(i)) + ", ");
            }
            writer.println("\n};");
        }
    }

    private void DumpGetNextToken(SourceWriter writer, LexerData data) {
        if ((data.getNextStateForEof() != null) || (data.getActionForEof() != null)) {
            writer.println("self.token_lexical_actions(matched_token);");
        }

        writer.println("return matched_token;");
        writer.println("      }");

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            writer.println("   image = jjimage;");
            writer.println("   image.setLength(0);");
            writer.println("   jjimage_len = 0;");
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
            writer.println(prefix + "   match self.cur_lex_state {");
            endSwitch = prefix + "   }";
            caseStr = prefix + " => {";
            prefix += "    ";
        }

        prefix += "   ";
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1)
                writer.println(i + caseStr);

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                writer.println("try { input_stream.backup(0);");
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1]
                        != 0L)) {
                    writer.println(
                            prefix + "   while ((cur_char < 64" + " && (0x" + Long.toHexString(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << cur_char)) != 0L) || \n" + prefix + "          (cur_char >> 6) == 1"
                                    + " && (0x"
                                    + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                                    + "L & (1L << (cur_char & 0o77))) != 0L)");
                }
                else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    writer.println(
                            prefix + "   while (cur_char <= " + (int) LexerGenerator.MaxChar(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << cur_char)) != 0L)");
                }
                else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    writer.println(prefix + "   while (cur_char > 63 && cur_char <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (cur_char & 0o77))) != 0L)");
                }

                if (data.options().getDebugTokenManager()) {
                    writer.println(prefix + "{");
                    writer.println("      debugStream.println("
                            + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                            + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \")\");");
                }
                writer.println(prefix + "      cur_char = input_stream.begin_token();");

                if (data.options().getDebugTokenManager()) {
                    writer.println(prefix + "}");
                }

                writer.println(prefix + "}");
                writer.println(prefix + "catch (java.io.IOException e1) { break 'EOFLoop; }");
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    writer.println(
                            "      debugStream.println(\"   Matched the empty string as \" + tokenImage["
                                    + data.initMatch(i) + "] + \" token.\");");
                }

                writer.println(prefix + "self.jjmatched_kind = " + data.initMatch(i) + ";");
                writer.println(prefix + "self.jjmatched_pos = usize::MAX;");
                writer.println(prefix + "cur_pos = 0;");
            }
            else {
                writer.println(
                        prefix + "self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
                writer.println(prefix + "self.jjmatched_pos = 0;");
            }

            if (data.options().getDebugTokenManager()) {
                writer.println("      debugStream.println("
                        + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
            }

            writer.println(prefix + "cur_pos = self.jj_move_string_literal_dfa0_" + i + "();");
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.println(prefix + "if self.jjmatched_pos < 0 || self.jjmatched_pos == 0 && self.jjmatched_kind > "
                            + data.canMatchAnyChar(i) + ") {");
                }
                else {
                    writer.println(
                            prefix + "if self.jjmatched_pos == 0 && self.jjmatched_kind > " + data.canMatchAnyChar(i) + " {");
                }

                if (data.options().getDebugTokenManager()) {
                    writer.println(
                            "           debugStream.println(\"   Current character matched as a \" + tokenImage["
                                    + data.canMatchAnyChar(i) + "] + \" token.\");");
                }
                writer.println(prefix + "   self.jjmatched_kind = " + data.canMatchAnyChar(i) + ";");

                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.println(prefix + "   self.jjmatched_pos = 0;");
                }

                writer.println(prefix + "}");
            }

            if (data.maxLexStates() > 1) {
                writer.println("}");
            }
        }

        if (data.maxLexStates() > 1) {
            writer.println("   _ => {}");
            writer.println(endSwitch);
        }
        else if (data.maxLexStates() == 0) {
            writer.println("       self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        }

        if (data.maxLexStates() > 1) {
            prefix = "  ";
        }
        else {
            prefix = "";
        }

        if (data.maxLexStates() > 0) {
            writer.println(
                    prefix + "   if self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
            writer.println(prefix + "      if self.jjmatched_pos + 1 < cur_pos {");

            if (data.options().getDebugTokenManager()) {
                writer.println(prefix + "         debugStream.println("
                        + "\"   Putting back \" + (cur_pos - jjmatched_pos - 1) + \" characters into the input stream.\");");
            }

            writer.println(prefix + "         self.input_stream.backup(cur_pos - self.jjmatched_pos - 1);");
            writer.println(prefix + "      }");

            if (data.options().getDebugTokenManager()) {
                writer.println("    debugStream.println("
                        + "\"****** FOUND A \" + tokenImage[jjmatched_kind] + \" MATCH "
                        + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatched_pos + 1))) + "
                        + "\") ******\\n\");");
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer
                        .println(prefix + "      if (JJTO_TOKEN[(self.jjmatched_kind >> 6) as usize] & "
                                + "(1 << (self.jjmatched_kind & 0o77))) != 0 {");
            }

            writer.println(prefix + "         matched_token = self.jj_fill_token();");

            if (data.hasSpecial()) {
                writer.println(prefix + "         matched_token.specialToken = specialToken;");
            }

            if (data.hasTokenActions()) {
                writer.println(prefix + "         self.token_lexical_actions(matched_token);");
            }

            if (data.maxLexStates() > 1) {
                writer.println("       if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
                writer.println(prefix + "       self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                writer.println("       }");
            }

            writer.println(prefix + "         return matched_token;");

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.println(prefix + "      }");

                if (data.hasSkip() || data.hasSpecial()) {
                    if (data.hasMore()) {
                        writer.println(
                                prefix + "      else if (self.JJTO_SKIP[self.jjmatched_kind >> 6] & "
                                        + "(1 << (jjmatched_kind & 0o77))) != 0");
                    }
                    else {
                        writer.println(prefix + "      else");
                    }

                    writer.println(prefix + "      {");

                    if (data.hasSpecial()) {
                        writer.println(
                                prefix + "         if (self.jjtoSpecial[self.jjmatched_kind >> 6] & "
                                        + "(1 << (self.jjmatched_kind & 0o77))) != 0 {");

                        writer.println(prefix + "            matched_token = self.jj_fill_token();");
                        writer.println(
                                prefix + "         }");

                        writer.println(prefix + "            if specialToken == null {");
                        writer.println(prefix + "               specialToken = matched_token;");
                        writer.println(prefix + "            } else {");
                        writer.println(prefix + "               matched_token.specialToken = specialToken;");
                        writer.println(prefix + "               specialToken = (specialToken.next = matched_token);");
                        writer.println(prefix + "            }");

                        if (data.hasSkipActions()) {
                            writer.println(prefix + "            self.skip_lexical_actions(matched_token);");
                        }

                        writer.println(prefix + "         }");

                        if (data.hasSkipActions()) {
                            writer.println(prefix + "         } else {");
                            writer.println(prefix + "            self.skip_lexical_actions(null);");
                            writer.println(prefix + "         }");
                        }
                    }
                    else if (data.hasSkipActions()) {
                        writer.println(prefix + "         self.skip_lexical_actions(null);");
                    }

                    if (data.maxLexStates() > 1) {
                        writer.println("         if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
                        writer.println(prefix + "         self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                        writer.println("         }");
                    }

                    writer.println(prefix + "         break 'EOFLoop;");
                    writer.println(prefix + "      }");
                }

                if (data.hasMore()) {
                    if (data.hasMoreActions()) {
                        writer.println(prefix + "      self.more_lexical_actions();");
                    }
                    else if (data.hasSkipActions() || data.hasTokenActions()) {
                        writer.println(prefix + "      self.jjimage_len += self.jjmatched_pos + 1;");
                    }

                    if (data.maxLexStates() > 1) {
                        writer.println("      if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 7");
                        writer.println(prefix + "      self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                        writer.println("      }");
                    }
                    writer.println(prefix + "      cur_pos = 0;");
                    writer.println(
                            prefix + "      self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

                    writer.println(prefix + "      try {");
                    writer.println(prefix + "         cur_char = input_stream.read_char();");

                    if (data.options().getDebugTokenManager()) {
                        writer.println("   debugStream.println("
                                + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                                + "\"Current character : \" + "
                                + "TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                                + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
                    }
                    writer.println(prefix + "         continue;");
                    writer.println(prefix + "      }");
                    writer.println(prefix + "      catch (java.io.IOException e1) { }");
                }
            }

            writer.println(prefix + "   }");
            writer.println("""
                 let mut error_line = self.input_stream.get_end_line();
                 let mut error_column = self.input_stream.get_end_column();
                 let mut error_after = String::new();
                 let mut eof_seen = false;
                 let result = self.input_stream.read_char();
                 if result.is_ok() {
                    self.input_stream.backup(1);
                 } else {
                    eof_seen = true;
                    if cur_pos <= 1 {
                      error_after = String::new();
                   } else {
                      error_after = self.input_stream.get_image();
                   }
                   if self.cur_char == '\\n'.try_into().unwrap() || self.cur_char == '\\r'.try_into().unwrap() {
                      error_line += 1;
                      error_column = 0;
                   } else {
                      error_column += 1;
                   }
                 }
                 if !eof_seen {
                    self.input_stream.backup(1);
                    if cur_pos <= 1 {
                       error_after = String::new();
                    } else {
                       error_after = self.input_stream.get_image();
                    }
                 }
                 panic!("Lexical error at line {}, column {} with error: {}", error_line, error_column, error_after);
                """);
        }

        if (data.hasMore()) {
            writer.println(prefix + " }");
        }
    }

    private void DumpSkipActions(SourceWriter writer, LexerData data) {
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
                    writer.println("         if (jjmatched_pos == -1)");
                    writer.println("         {");
                    writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    writer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    writer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    writer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    writer.println(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();");
                    writer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
                    writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    writer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");
                if (data.getImage(i) != null) {
                    writer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                    writer.println("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();");
                }
                else {
                    writer.println(
                            "(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));");
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

    private void DumpMoreActions(SourceWriter writer, LexerData data) {
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
                    writer.println("         if (jjmatched_pos == -1)");
                    writer.println("         {");
                    writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    writer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    writer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    writer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    writer.println(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();");
                    writer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
                    writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    writer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");

                if (data.getImage(i) != null) {
                    writer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                }
                else {
                    writer.println("(input_stream.GetSuffix(jjimage_len));");
                }

                writer.println("         jjimage_len = 0;");
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

    private void DumpTokenActions(SourceWriter writer, LexerData data) {
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
                    writer.println("         if (jjmatched_pos == -1)");
                    writer.println("         {");
                    writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    writer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    writer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    writer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    writer.println(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();");
                    writer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
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
                    writer.append("        image.append");

                    if (data.getImage(i) != null) {
                        writer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                        writer.println("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();");
                    }
                    else {
                        writer.println(
                                "(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));");
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

    private void DumpStateSets(SourceWriter writer, LexerData data) {
        int cnt = 0;
        int length = 0;
        for (int[] set : data.getOrderedStateSet()) {
            for (int element : set) {
                length++;
            }
        }

        writer.append("const JJNEXT_STATES : [usize; " + length + "] = [");
        if (!data.getOrderedStateSet().isEmpty()) {
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        writer.append("\n   ");
                    }

                    writer.append(element + ", ");
                }
            }
        }
        else {
            writer.append("0");
        }

        writer.println("\n];");
    }

    private static List<String> getStrLiteralImageList(LexerData data) {
        var list = new ArrayList<String>();
        if (data.getImageCount() <= 0) {
            return list;
        }

        String image;
        int i;
        int charCnt = 0; // Set to zero in reInit() but just to be sure

        data.setImage(0, "");

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
                    charCnt = 0;
                }

                list.add(null);
                continue;
            }

            String toPrint = "";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("0o" + Integer.toOctalString(image.charAt(j))) + ";";
                }
                else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0x" + hexVal + ";";
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            if ((charCnt += toPrint.length()) >= 80) {
                charCnt = 0;
            }

            list.add(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                charCnt = 0;
            }
            list.add(null);;
        }
        return list;
    }

    private void DumpStartWithStates(SourceWriter writer, NfaStateData data) {
        writer.println(
                "private int " + "jjStartNfaWithStates" + data.getLexerStateSuffix()
                        + "(int pos, int kind, int state)");
        writer.println("{");
        writer.println("   jjmatched_kind = kind;");
        writer.println("   jjmatched_pos = pos;");

        if (data.global.options().getDebugTokenManager()) {
            writer.println(
                    "   debugStream.println(\"   No more string literal token matches are possible.\");");
            writer.println("   debugStream.println(\"   Currently matched the first \" "
                    + "+ (jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
        }

        writer.println("   try { cur_char = input_stream.read_char(); }");
        writer.println("   catch(java.io.IOException e) { return pos + 1; }");

        if (data.global.options().getDebugTokenManager()) {
            writer.println("   debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
        }
        writer.println("   return jj_move_nfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
        writer.println("}");
    }

    @Override
    protected final void DumpHeadForCase(SourceWriter writer, int byteNum) {
        if (byteNum == 0)
            writer.println("         let l: u64 = 1u64 << self.cur_char;");
        else if (byteNum == 1)
            writer.println("         let l: u64 = 1u64 << (self.cur_char & 0o77);");
        else {
            writer.println("         let hi_byte: u32 = self.cur_char >> 8;");
            writer.println("         let l1: u64 = 1u64 << (hi_byte & 0o77);");
            writer.println("         let l2: u64 = 1u64 << (self.cur_char & 0o77);");
            writer.println("         let i1: usize = (hi_byte >> 6) as usize;");
            writer.println("         let i2: usize = ((self.cur_char & 0xff) >> 6) as usize;");
        }

        // writer.println(" MatchLoop: do");
        writer.println("         let mut while_cond = true;");
        writer.println("         while while_cond {");
        writer.println("            i -= 1;");
        writer.println("            match self.jjstate_set[i] {");
    }

    private void DumpNonAsciiMoveMethod(NfaState state, LexerData data, SourceWriter writer) {
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            writer.println("      " + state.loByteVec.get(j) + " => {");
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1))))
                writer.println("         return (JJBIT_VEC" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0;");
            else
                writer.println("         return true;");
            writer.println("      }");
        }

        writer.println("      _ => {");
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2])))
                writer.println("         if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0 {");
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                writer.println("            if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0 {");
                writer.println("               return false;");
                writer.println("            } else {");
            }
            writer.println("            return true;");
            writer.println("         }");
        }
        writer.println("         false");
        writer.println("      }");
    }

    private String getStatesForState(LexerData data) {
        if (data.getStatesForState() == null) {
            assert (false) : "This should never be null.";
            return "";
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
            return "";
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
    protected final void dumpNfaStartStatesCode(SourceWriter writer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) // No need to generate this function
            return;

        int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;
        int ind;

        StringBuilder params = new StringBuilder();
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            params.append("active").append(i).append(": u64, ");
        }
        params.append("active").append(i).append(": u64)");

        writer.println("fn jjStopStringLiteralDfa" + data.getLexerStateSuffix() +
            "(&self, pos: usize, " + params+ " -> usize {");

        if (data.global.options().getDebugTokenManager())
            writer.println("      debugStream.println(\"   No more string literal token matches are possible.\");");

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
                        writer.append(" || ");
                    }
                    else {
                        writer.append("         if (");
                    }

                    condGenerated = true;

                    writer.append("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
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
                            writer.println("            jjmatched_kind = " + kindStr + ";");

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE))) {
                                writer.println("            jjmatched_pos = 0;");
                            }
                        }
                        else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                writer.println("            if (jjmatched_pos != " + i + ")");
                                writer.println("            {");
                                writer.println("               jjmatched_kind = " + kindStr + ";");
                                writer.println("               jjmatched_pos = " + i + ";");
                                writer.println("            }");
                            }
                            else {
                                writer.println("            jjmatched_kind = " + kindStr + ";");
                                writer.println("            jjmatched_pos = " + i + ";");
                            }
                        }
                        else {
                            if (jjmatchedPos > 0) {
                                writer.println("            if (jjmatched_pos < " + jjmatchedPos + ")");
                            }
                            else {
                                writer.println("            if (jjmatched_pos == 0)");
                            }
                            writer.println("            {");
                            writer.println("               jjmatched_kind = " + kindStr + ";");
                            writer.println("               jjmatched_pos = " + jjmatchedPos + ";");
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

        writer.append("private final int jjStartNfa" + data.getLexerStateSuffix() + params);
        writer.println("{");

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                writer.println(
                        "   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data)
                                + ", pos + 1);");
            }
            else {
                writer.println("   return pos + 1;");
            }

            writer.println("}");
            return;
        }

        writer.append("   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
                + data.getLexerStateSuffix() + "(pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            writer.append("active" + i + ", ");
        }
        writer.append("active" + i + ")");
        writer.println(", pos + 1);");
        writer.println("}");
    }

    @Override
    protected final void dumpDfaCode(SourceWriter writer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        if (data.getMaxLen() == 0) {
            writer.println("fn jj_move_string_literal_dfa0" + data.getLexerStateSuffix() + "(&self) -> usize ");
            DumpNullStrLiterals(writer, data);
            return;
        }

        if (!data.global.boilerPlateDumped) {
            writer.println("fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {");
            writer.println("   self.jjmatched_kind = kind;");
            writer.println("   self.jjmatched_pos = pos;");

            if (data.global.options().getDebugTokenManager()) {
                writer.println(
                        "   debugStream.println(\"   No more string literal token matches are possible.\");");
                writer.println(
                        "   debugStream.println(\"   Currently matched the first \" + (jjmatched_pos + 1) + "
                                + "\" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
            }

            writer.println("   pos + 1");
            writer.println("}");
            data.global.boilerPlateDumped = true;
        }

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            String[] keys = LexerGenerator.ReArrange(tab);

            StringBuilder params = new StringBuilder();
            params.append("(&mut self");
            if (i != 0) {
                if (i == 1) {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= data.getMaxLenForActive(j)) {
                            params.append(", active").append(j).append(": u64");
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        params.append(", active").append(j).append(": u64");
                    }
                }
                else {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            params.append(", old").append(j).append(": u64, ")
                                .append("active_old").append(j).append(": u64");
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        params.append(", old").append(j).append(": u64, ")
                            .append("active_old").append(j).append(": u64");
                    }
                }
            }
            params.append(")");

            writer.println("fn jj_move_string_literal_dfa" + i + data.getLexerStateSuffix() + params + " -> usize {");
            if (i != 0) {
                if (i > 1) {
                    var atLeastOne = false;
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(" | ");
                            else
                                atLeastOne = true;
                            writer.println("   let active" + j + " = active_old" + j + " & old" + j + ";");
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        writer.println("   let active" + j + " = active_old" + j + " & old" + j + ";");
                    }

                    atLeastOne = false;
                    writer.append("   if (");

                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(" | ");
                            else
                                atLeastOne = true;
                            writer.append("active" + j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne)
                            writer.append(" | ");
                        writer.append("active" + j);
                    }

                    writer.println(") == 0 {");
                    if (!data.isMixedState() && (data.generatedStates() != 0)) {
                        writer.append("      return self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1))
                                writer.append("old" + j + ", ");
                            else
                                writer.append("0, ");
                        }
                        if (i <= (data.getMaxLenForActive(j) + 1))
                            writer.println("old" + j + ");");
                        else
                            writer.println("0);");
                    }
                    else if (data.generatedStates() != 0)
                        writer.println("      return self.jj_move_nfa" + data.getLexerStateSuffix() +
                            "(" + InitStateName(data) + ", " + (i - 1) + ");");
                    else
                        writer.println("      return " + i + ";");
                    writer.append("   }");
                }

                if (data.global.options().getDebugTokenManager()) {
                    writer.println("   if self.jjmatched_kind != 0 && vjjmatchedKind != 0x" + Integer.toHexString(
                                    Integer.MAX_VALUE) + " {");
                    writer.println("      debugStream.println(\"   Currently matched the first \" + "
                            + "(self.jjmatched_pos + 1) + \" characters as a \" + self.tokenImage[self.jjmatched_kind as usize] + \" token.\");");
                    writer.println("   debugStream.println(\"   Possible string literal matches : { \"");

                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            writer.println(" +");
                            writer.append("         self.jjKindsForBitVector(" + vecs + ", ");
                            writer.append("active" + vecs + ") ");
                        }
                    }

                    writer.println(" + \" } \");");
                    writer.println("   }");
                }
                writer.new_line();

                writer.println("   let result = self.input_stream.read_char();");
                writer.println("   if result.is_err() {");

                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    writer.append("      self.jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k)) {
                            writer.append("active" + k + ", ");
                        }
                        else {
                            writer.append("0, ");
                        }
                    }

                    if (i <= data.getMaxLenForActive(k))
                        writer.println("active" + k + ");");
                    else
                        writer.println("0);");

                    if (data.global.options().getDebugTokenManager()) {
                        writer.println("      if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x" +
                            Integer.toHexString(Integer.MAX_VALUE));
                        writer.println("         debugStream.println(\"   Currently matched the first \" + "
                                + "(jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
                    }
                    writer.println("      return " + i + ";");
                }
                else if (data.generatedStates() != 0)
                    writer.println("     return self.jj_move_nfa" + data.getLexerStateSuffix() +
                                "(" + InitStateName(data) + ", " + (i - 1) + ");");
                else
                    writer.println("      return " + i + ";");

                writer.println("   }");
                writer.println("   self.cur_char = u32::from(result.unwrap());");
                writer.new_line();
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                writer.println("   debugStream.println("
                        + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
            }

            writer.println("   match self.cur_char {");

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
                        writer.println("      " + (int) Character.toUpperCase(c) + " => {");
                    }

                    if (c != Character.toLowerCase(c)) {
                        writer.println("      " + (int) Character.toLowerCase(c) + " => {");
                    }
                }

                writer.println("      " + (int) c + " => {");

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

                            if (ifGenerated)
                                writer.append("         else if ");
                            else if (i != 0)
                                writer.append("         if ");

                            ifGenerated = true;

                            int kindToPrint;
                            if (i != 0) {
                                writer.println("(active" + j + " & 0x" + Long.toHexString(1L << k) + ") != 0");
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
                                            prefix + "return self.jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i
                                                    + ", " + kindToPrint + ", " + stateSetName + ");");
                                }
                                else {
                                    writer.println(prefix + "return self.jj_stop_at_pos" + "(" + i + ", " + kindToPrint + ");");
                                }
                                writer.println("      }");
                            }
                            else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                writer.println("         {");
                                writer.println(prefix + "self.jjmatched_kind = " + kindToPrint + ";");
                                writer.println(prefix + "self.jjmatched_pos = " + i + ";");
                                writer.println("         }");
                            }
                            else {
                                writer.println(prefix + "self.jjmatched_kind = " + kindToPrint + ";");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        writer.append("         return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= data.getMaxLenForActive(j)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                writer.append("0x" + Long.toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                writer.append(", ");

                            writer.append("0x" + Long.toHexString(info.validKinds[j]));
                        }
                        writer.println(");");
                        writer.println("      }");
                    }
                    else {
                        writer.append("         return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                                else
                                    writer.append("active" + j + ", 0");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(", ");

                            if (info.validKinds[j] != 0L)
                                writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                            else
                                writer.append("active" + j + ", 0");
                        }

                        writer.println(");");
                        writer.println("      }");
                    }
                }
                else {// A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            writer.println(
                                "         return self.jj_move_nfa" + data.getLexerStateSuffix() + "("
                                    + InitStateName(
                                    data) + ", 0);");
                        }
                        else {
                            writer.println("         return 1;");
                        }
                    }
                    else if (i != 0) // No more str literals to look for
                    {
                        startNfaNeeded = true;
                    }
                    writer.println("      }");
                }
            }

            writer.println("      _ => {");

            if (data.global.options().getDebugTokenManager()) {
                writer.println("      debugStream.println(\"   No string literal matches possible.\");");
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    writer.println("         return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(
                                            data) + ", 0);");
                }
                else
                    startNfaNeeded = true;
            }
            else {
                writer.println("         return " + (i + 1) + ";");
            }
            writer.println("      }");
            writer.println("   }");

            if ((i != 0) && startNfaNeeded) {
                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    /*
                     * Here, a string literal is successfully matched and no more string literals are
                     * possible. So set the kind and state set upto and including this position for the
                     * matched string.
                     */

                    writer.append("   self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            writer.append("active" + k + ", ");
                        else
                            writer.append("0, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        writer.println("active" + k + ")");
                    else
                        writer.println("0)");
                }
                else if (data.generatedStates() != 0)
                    writer.println("   self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "  + i + ")");
                else
                    writer.println("   return " + (i + 1));
            }

            writer.println("}");
            writer.new_line();
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(writer, data);
        }
    }

    @Override
    protected final void dumpMoveNfa(SourceWriter writer, NfaStateData data) {
        writer.println("fn jj_move_nfa" + data.getLexerStateSuffix() + "(&mut self, start_state: usize, cur_pos: usize) -> usize {");
        if (data.generatedStates() == 0) {
            writer.println("   return cur_pos;");
            writer.println("}");
            return;
        }

        if (data.isMixedState()) {
            writer.println("""
   let str_kind = self.jjmatched_kind;
   let str_pos = self.jjmatched_pos;
   let seen_upto: usize = cur_pos + 1;
   self.input_stream.backup(seen_upto);
   let result = self.input_stream.read_char();
   if result.is_err() {
      panic!("Internal Error");
   }
   self.cur_char = u32::from(result.unwrap());
   let mut cur_pos: usize = 0;
""");
        }

        writer.println("   let mut starts_at: usize = 0;");
        writer.println("   self.jjnew_state_cnt = " + data.generatedStates() + ";");
        writer.println("   let mut i: usize = 1;");
        writer.println("   self.jjstate_set[0] = start_state;");

        if (data.global.options().getDebugTokenManager()) {
            writer.println("      debugStream.println(\"   Starting NFA to match one of : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, 0, 1));");
            writer.println("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
        }

        writer.println("   let mut kind: u32 = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        writer.println("   loop {");
        writer.println("      self.jjround += 1;");
        writer.println("      if self.jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        writer.println("         self.re_init_rounds();");
        writer.println("      }");

        writer.println("      if self.cur_char < 64 {");
        DumpAsciiMoves(writer, data, 0);
        writer.println("      }");

        writer.println("      else if self.cur_char < 128 {");
        DumpAsciiMoves(writer, data, 1);
        writer.println("      }");

        writer.println("      else {");
        DumpCharAndRangeMoves(writer, data);
        writer.println("      }");
        writer.println("      if kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        writer.println("         self.jjmatched_kind = kind;");
        writer.println("         self.jjmatched_pos = cur_pos;");
        writer.println("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        writer.println("      }");
        writer.println("      cur_pos += 1;");

        if (data.global.options().getDebugTokenManager()) {
            writer.println("      if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                + Integer.toHexString(Integer.MAX_VALUE) + " {");
            writer.println("         debugStream.println("
                    + "\"   Currently matched the first \" + (jjmatched_pos + 1) + \" characters as"
                    + " a \" + tokenImage[jjmatched_kind] + \" token.\");");
            writer.println("      }");
        }

        writer.println("      i = self.jjnew_state_cnt;");
        writer.println("      self.jjnew_state_cnt = starts_at;");
        writer.println("      starts_at = " + data.generatedStates() + " - self.jjnew_state_cnt;");
        writer.println("      if i == starts_at {");
        if (data.isMixedState())
            writer.println("         break;");
        else
            writer.println("         return cur_pos;");
        writer.println("      }");

        if (data.global.options().getDebugTokenManager()) {
            writer.println("      debugStream.println(\"   Possible kinds of longer matches : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, starts_at, i));");
        }

        writer.println("      let result = self.input_stream.read_char();");
        writer.println("      if result.is_err() {");
        if (data.isMixedState())
            writer.println("         break;");
        else
            writer.println("         return cur_pos;");
        writer.println("      }");
        writer.println("      self.cur_char = u32::from(result.unwrap());");

        if (data.global.options().getDebugTokenManager()) {
            writer.println("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
        }
        writer.println("   }");

        if (data.isMixedState()) {
            writer.println("""
   if self.jjmatched_pos > str_pos {
      return cur_pos;
   }

   let to_ret = cmp::max(cur_pos, seen_upto);
   if cur_pos < to_ret {
      let mut i = cmp::min(cur_pos, seen_upto);
      while i > 0 {
         let result = self.input_stream.read_char();
         if result.is_err() {
            panic!("Internal Error : Please send a bug report.");
         }
         self.cur_char = u32::from(result.unwrap());
         i -= 1;
      }
   }
   if self.jjmatched_pos < str_pos {
      self.jjmatched_kind = str_kind;
      self.jjmatched_pos = str_pos;
   }
   else if self.jjmatched_pos == str_pos && self.jjmatched_kind > str_kind {
      self.jjmatched_kind = str_kind;
   }

   to_ret
""");
        }
        writer.println("}");
    }
}
