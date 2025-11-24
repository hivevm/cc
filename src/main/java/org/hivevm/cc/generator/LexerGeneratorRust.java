// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.core.SourceWriter;

/**
 * The {@link LexerGeneratorRust} class.
 */
public abstract class LexerGeneratorRust extends LexerGenerator {

    private String PrintNoBreak(SourceWriter writer, NfaStateData data, NfaState state, int byteNum,
                                boolean[] dumped) {
        if (state.inNextOf != 1) {
            throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu");
        }

        dumped[state.stateName] = true;

        if (byteNum >= 0) {
            if (state.asciiMoves[byteNum] != 0L) {
                writer.println("               " + state.stateName + " => {");
                DumpAsciiMoveForCompositeState(writer, data, state, byteNum, false);
                writer.println("               }");
                return "";
            }
        }
        else if (state.nonAsciiMethod != -1) {
            writer.println("               " + state.stateName + " => {");
            DumpNonAsciiMoveForCompositeState(writer, data, state);
            writer.println("               }");
            return "";
        }

        return ("               " + state.stateName + " => {\n");
    }

    private void DumpCompositeStatesNonAsciiMoves(SourceWriter writer, NfaStateData data, String key,
                                                  boolean[] dumped) {
        int[] nameSet = data.getNextStates(key);
        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;
        NfaState stateForCase = null;
        String toPrint = "";
        boolean stateBlock = (data.stateBlockTable.get(key) != null);

        for (int i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.nonAsciiMethod != -1) {
                if (neededStates++ == 1) {
                    break;
                }
                else {
                    toBePrinted = tmp;
                }
            }
            else {
                dumped[tmp.stateName] = true;
            }

            if (tmp.stateForCase != null) {
                if (stateForCase != null) {
                    throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : ");
                }

                stateForCase = tmp.stateForCase;
            }
        }

        if (stateForCase != null) {
            toPrint = PrintNoBreak(writer, data, stateForCase, -1, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && toPrint.equals("")) {
                writer.println("                  break;");
                writer.println("               }");
            }

            return;
        }

        if (neededStates == 1) {
            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            var cases = new ArrayList<String>();
            cases.add("" + stateNameForComposite(data, key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1))
                cases.add("" + toBePrinted.stateName);
            writer.println("               " + String.join(" | ", cases) + " => {");

            dumped[toBePrinted.stateName] = true;
            DumpNonAsciiMove(writer, data, toBePrinted, dumped);
            writer.println("               }");
            return;
        }

        if (!toPrint.equals("")) {
            writer.append(toPrint);
        }

        int keyState = stateNameForComposite(data, key);
        writer.println("               " + keyState + " => {");
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (int i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.nonAsciiMethod != -1) {
                if (stateBlock) {
                    dumped[tmp.stateName] = true;
                }
                DumpNonAsciiMoveForCompositeState(writer, data, tmp);
            }
        }

        writer.println("               }");
    }

    private void DumpAsciiMove(SourceWriter writer, NfaStateData data, NfaState state, int byteNum,
                               boolean[] dumped, List<String> cases) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;

        for (NfaState element : data.getAllStates()) {

            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (onlyState && ((state.asciiMoves[byteNum] & element.asciiMoves[byteNum]) != 0L)) {
                onlyState = false;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.asciiMoves[byteNum]
                    == element.asciiMoves[byteNum])
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                cases.add("" + element.stateName);
            }
        }

        if (!cases.isEmpty())
            writer.println("               " + String.join(" | ", cases) + " => {");

        int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
                && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
                && (state.kindToPrint != Integer.MAX_VALUE))) {
            String kindCheck = "";

            if (!onlyState) {
                kindCheck = " && kind > " + state.kindToPrint;
            }

            if (oneBit != -1)
                writer.println("                  if self.cur_char == " + ((64 * byteNum) + oneBit) + kindCheck + " {");
            else
                writer.println("                  if (" + toHexString(state.asciiMoves[byteNum])
                    + " & l) != 0" + kindCheck + " {");

            writer.println("                     kind = " + state.kindToPrint + ";");
            writer.println("                  }");

            return;
        }

        String prefix = "";
        boolean hasIf = false;
        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (oneBit != -1) {
                writer.println("                  if self.cur_char != " + ((64 * byteNum) + oneBit) + " {");
                writer.println("                     break;");
                writer.println("                  }");
            }
            else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                writer.println("                  if (" + toHexString(state.asciiMoves[byteNum]) + " & l) == 0 {");
                writer.println("                     break;");
                writer.println("                  }");
            }

            if (onlyState) {
                writer.println("                  kind = " + state.kindToPrint + ";");
            }
            else {
                writer.println("                  if kind > " + state.kindToPrint + " {");
                writer.println("                     kind = " + state.kindToPrint + ";");
                writer.println("                  }");
            }
        }
        else if (oneBit != -1) {
            writer.println("                  if self.cur_char == " + ((64 * byteNum) + oneBit) + " {");
            prefix = "   ";
            hasIf = true;
        }
        else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            writer.println("                  if (" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0 {");
            prefix = "   ";
            hasIf = true;
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    writer.println(prefix + "                  self.jj_check_n_add(" + name + ");");
                }
                else {
                    writer.println(prefix + "                  self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                    writer.println(prefix + "                  self.jjnew_state_cnt += 1;");
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.println(prefix + "                  self.jj_check_n_add_two_states("
                    + stateNames[0] + ", " + stateNames[1] + ");");
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  self.jj_check_n_add_states(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.println(");");
                }
                else {
                    writer.println(prefix + "                  self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        if (hasIf)
            writer.println("                  }");
    }


    private void DumpAsciiMoveForCompositeState(SourceWriter writer, NfaStateData data, NfaState state,
                                                int byteNum,
                                                boolean elseNeeded) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        // System.out.println(stateName + " \'s nextIntersects : " + nextIntersects);
        String prefix = "";
        boolean hasIf = false;
        if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);

            if (oneBit != -1) {
                writer.println("                  " + (elseNeeded ? "else " : "")
                    + "if self.cur_char == " + ((64 * byteNum) + oneBit) + " {");
            }
            else {
                writer.println("                  " + (elseNeeded ? "else " : "")
                    + "if (" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0 {");
            }
            prefix = "   ";
            hasIf = true;
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                writer.println("                  {");
            }

            writer.println(prefix + "                  if kind > " + state.kindToPrint + " {");
            writer.println(prefix + "                     kind = " + state.kindToPrint + ";");
            writer.println(prefix + "                  }");
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];

                if (nextIntersects)
                    writer.println(prefix + "                  self.jj_check_n_add(" + name + ");");
                else {
                    writer.println(prefix + "                  self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                    writer.println(prefix + "                  self.jjnew_state_cnt += 1;");
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.println(
                        prefix + "                  self.jj_check_n_add_two_states(" + stateNames[0] + ", "
                                + stateNames[1] + ");");
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  self.jj_check_n_add_states(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.println(");");
                }
                else {
                    writer.println(prefix + "                  self.jj_add_states(" + indices[0] + ", " + indices[1]
                                    + ");");
                }
            }
        }

        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint
                != Integer.MAX_VALUE)) {
            writer.println("                  }");
        }

        if (hasIf)
            writer.println("                  }");
    }

    private void DumpNonAsciiMoveForCompositeState(SourceWriter writer, NfaStateData data,
                                                   NfaState state) {
        boolean nextIntersects = state.selfLoop();
        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        writer.println("                  if (jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2))");

        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.println("                  {");
            writer.println("                     if kind > " + state.kindToPrint + "{");
            writer.println("                        kind = " + state.kindToPrint + ";");
            writer.println("                     }");
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects)
                    writer.println("                     self.jj_check_n_add(" + name + ");");
                else {
                    writer.println("                     self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                    writer.println("                     self.jjnew_state_cnt += 1;");
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.println(
                        "                     self.jj_check_n_add_two_states(" + stateNames[0] + ", " + stateNames[1]
                                + ");");
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append("                     self.jj_check_n_add_states(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.println(");");
                }
                else {
                    writer.println(
                            "                     self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.println("                  }");
        }
    }

    private void DumpNonAsciiMove(SourceWriter writer, NfaStateData data, NfaState state,
                                  boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        var cases = new ArrayList<String>();
        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.nonAsciiMethod
                    == element.nonAsciiMethod)
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                cases.add("" + element.stateName);
            }
        }

        if (!cases.isEmpty())
            writer.println("               " + String.join(" | ", cases) + " => {");

        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            String kindCheck = " && kind > " + state.kindToPrint;

            writer.println("                  if jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2)"
                            + kindCheck + " {");
            writer.println("                     kind = " + state.kindToPrint + ";");
            writer.println("                  }");
            return;
        }

        String prefix = "   ";
        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.println("                  if !jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2) {");
            writer.println("                     break;");
            writer.println("                  }");

            writer.println("                  if kind > " + state.kindToPrint + " {");
            writer.println("                     kind = " + state.kindToPrint + ";");
            writer.println("                  }");
            prefix = "";
        }
        else {
            writer.println("                  if jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2) {");
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    writer.println(prefix + "                  self.jj_check_n_add(" + name + ");");
                }
                else {
                    writer.println(prefix + "                  self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                    writer.println(prefix + "                  self.jjnew_state_cnt += 1;");
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.println(
                        prefix + "                  self.jj_check_n_add_two_states(" + stateNames[0] + ", "
                                + stateNames[1] + ");");
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  self.jj_check_n_add_states(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.println(");");
                }
                else {
                    writer.println(prefix
                        + "                  self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        if (state.kindToPrint == Integer.MAX_VALUE) {
            writer.println("                  }");
        }
    }


    private void DumpCompositeStatesAsciiMoves(SourceWriter writer, NfaStateData data, String key,
                                               int byteNum,
                                               boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;
        NfaState stateForCase = null;
        String toPrint = "";
        boolean stateBlock = (data.stateBlockTable.get(key) != null);

        for (i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
                if (neededStates++ == 1) {
                    break;
                }
                else {
                    toBePrinted = tmp;
                }
            }
            else {
                dumped[tmp.stateName] = true;
            }

            if (tmp.stateForCase != null) {
                if (stateForCase != null) {
                    throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : ");
                }

                stateForCase = tmp.stateForCase;
            }
        }

        if (stateForCase != null) {
            toPrint = PrintNoBreak(writer, data, stateForCase, byteNum, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && toPrint.equals("")) {
                writer.println("                  break;");
                writer.println("               }");
            }
            return;
        }

        if (neededStates == 1) {
            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            var cases = new ArrayList<String>();
            cases.add("" + stateNameForComposite(data, key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1))
                cases.add("" + toBePrinted.stateName);
            writer.println("               " + String.join(" | ", cases) + " => {");

            dumped[toBePrinted.stateName] = true;
            DumpAsciiMove(writer, data, toBePrinted, byteNum, dumped, new ArrayList<>());
            return;
        }

        List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

        if (!toPrint.equals("")) {
            writer.append(toPrint);
        }

        int keyState = stateNameForComposite(data, key);
        writer.println("               " + keyState + " => {");
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (i = 0; i < partition.size(); i++) {
            List<NfaState> subSet = partition.get(i);

            for (int j = 0; j < subSet.size(); j++) {
                tmp = subSet.get(j);

                if (stateBlock) {
                    dumped[tmp.stateName] = true;
                }
                DumpAsciiMoveForCompositeState(writer, data, tmp, byteNum, j != 0);
            }
        }

        writer.println("               }");
    }

    protected final void DumpAsciiMoves(SourceWriter writer, NfaStateData data, int byteNum) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        Enumeration<String> e = data.compositeStateTable.keys();

        DumpHeadForCase(writer, byteNum);

        while (e.hasMoreElements()) {
            DumpCompositeStatesAsciiMoves(writer, data, e.nextElement(), byteNum, dumped);
        }

        for (NfaState element : data.getAllStates()) {

            if (dumped[element.stateName] || (element.lexState != data.getStateIndex())
                    || !element.HasTransitions() || element.dummy
                    || (element.stateName == -1)) {
                continue;
            }

            String toPrint = "";

            if (element.stateForCase != null) {
                if ((element.inNextOf == 1) || dumped[element.stateForCase.stateName]) {
                    continue;
                }

                toPrint = PrintNoBreak(writer, data, element.stateForCase, byteNum, dumped);

                if (element.asciiMoves[byteNum] == 0L) {
                    if (toPrint.equals("")) {
                        writer.println("                  break;");
                    }

                    continue;
                }
            }

            if (element.asciiMoves[byteNum] == 0L) {
                continue;
            }

            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            dumped[element.stateName] = true;
            var cases = new ArrayList<String>();
            cases.add("" + element.stateName);
            DumpAsciiMove(writer, data, element, byteNum, dumped, cases);
            writer.println("               }");
        }

        if ((byteNum != 0) && (byteNum != 1))
            writer.println("""
                           _ => {
                              if i1 == 0 || l1 == 0 || i2 == 0 || l2 == 0 {
                                 break;
                              } else {
                                 break;
                              }
                           }
            """);
        else
            writer.println("               _ => {}");

        writer.println("            }");
        writer.println("            while_cond = i != starts_at;");
        writer.println("         }");
    }


    protected final void DumpCharAndRangeMoves(SourceWriter writer, NfaStateData data) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        Enumeration<String> e = data.compositeStateTable.keys();
        int i;

        DumpHeadForCase(writer, -1);

        while (e.hasMoreElements()) {
            DumpCompositeStatesNonAsciiMoves(writer, data, e.nextElement(), dumped);
        }

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState temp = data.getAllState(i);

            if ((temp.stateName == -1) || dumped[temp.stateName]
                || (temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy) {
                continue;
            }

            String toPrint = "";

            if (temp.stateForCase != null) {
                if ((temp.inNextOf == 1) || dumped[temp.stateForCase.stateName])
                    continue;

                toPrint = PrintNoBreak(writer, data, temp.stateForCase, -1, dumped);

                if (temp.nonAsciiMethod == -1) {
                    if (toPrint.equals(""))
                        writer.println("                  break;");
                    continue;
                }
            }

            if (temp.nonAsciiMethod == -1)
                continue;

            if (!toPrint.equals(""))
                writer.append(toPrint);

            dumped[temp.stateName] = true;
            // System.out.println("case : " + temp.stateName);
            writer.println("               " + temp.stateName + " => {");
            DumpNonAsciiMove(writer, data, temp, dumped);
            writer.println("               }");
        }

        writer.println("""
                           _ => {
                              if i1 == 0 || l1 == 0 || i2 == 0 || l2 == 0 {
                                 break;
                              } else {
                                 break;
                              }
                           }
            """);
        writer.println("            }");
        writer.println("            while_cond = i != starts_at;");
        writer.println("         }");
    }

    @Override
    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value);
    }
}
