// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.TokenKind;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.model.RegExprSpec;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Non-deterministic Finite Automaton.
 */
record Nfa(NfaState start, NfaState end) {

    Nfa(NfaStateData data) {
        this(new NfaState(data), new NfaState(data));
    }

    /**
     * Main NFA construction loop: processes all token productions and builds the NFA transitions.
     */
    static void buildLexer(LexerData data, Map<String, List<TokenProduction>> allTpsForState,
                                  List<RExpression> choices) {
        RExpression curRE;

        for (String key : allTpsForState.keySet()) {
            NfaStateData stateData = data.newStateData(key);
            stateData.getInitialState();

            data.singlesToSkip[stateData.getStateIndex()] = new NfaState(stateData);
            data.singlesToSkip[stateData.getStateIndex()].dummy = true;

            if (key.equals("DEFAULT")) {
                data.defaultLexState = stateData.getStateIndex();
            }

            boolean ignoring = false;
            List<TokenProduction> allTps = allTpsForState.get(key);
            for (int i = 0; i < allTps.size(); i++) {
                TokenProduction tp = allTps.get(i);
                TokenKind kind = tp.getKind();
                boolean ignore = tp.isIgnoreCase();

                if (i == 0) {
                    ignoring = ignore;
                }

                for (RegExprSpec respec : tp.getRespecs()) {
                    curRE = respec.rexp;

                    data.rexprs[data.curKind = curRE.getOrdinal()] = curRE;
                    data.lexStates[curRE.getOrdinal()] = stateData.getStateIndex();
                    data.ignoreCase[curRE.getOrdinal()] = ignore;

                    if (curRE.isPrivateExp()) {
                        continue;
                    }

                    if (!data.getNoDfa() && (curRE instanceof RStringLiteral)
                            && !((RStringLiteral) curRE).getImage().isEmpty()) {
                        StringLiteralAnalyzer.generateDfa(stateData, (RStringLiteral) curRE);
                        if ((i != 0) && !stateData.isMixedState() && (ignoring != ignore)) {
                            stateData.hasMixed = true;
                        }
                    } else if (curRE.CanMatchAnyChar()) {
                        if ((data.canMatchAnyChar[stateData.getStateIndex()] == -1)
                                || (data.canMatchAnyChar[stateData.getStateIndex()]
                                > curRE.getOrdinal())) {
                            data.canMatchAnyChar[stateData.getStateIndex()] = curRE.getOrdinal();
                        }
                    } else {
                        Nfa temp;

                        if (curRE instanceof RChoice) {
                            choices.add(curRE);
                        }

                        temp = curRE.accept(new NfaVisitor(ignore), stateData);
                        temp.end().isFinal = true;
                        temp.end().kind = curRE.getOrdinal();
                        stateData.getInitialState().AddMove(temp.start());
                    }


                    if ((respec.nextState != null) && !respec.nextState.equals(
                            data.getStateName(stateData.getStateIndex()))) {
                        data.newLexState[curRE.getOrdinal()] = respec.nextState;
                    }

                    if ((respec.act != null) && !respec.act.getActionTokens().isEmpty()) {
                        data.actions[curRE.getOrdinal()] = respec.act;
                    }

                    switch (kind) {
                        case SPECIAL:
                            data.hasSkipActions |=
                                    (data.actions[curRE.getOrdinal()] != null) || (
                                            data.newLexState[curRE.getOrdinal()] != null);
                            data.hasSpecial = true;
                            data.toSpecial[curRE.getOrdinal() / 64] |=
                                    1L << (curRE.getOrdinal() % 64);
                            data.toSkip[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
                            break;
                        case SKIP:
                            data.hasSkipActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.hasSkip = true;
                            data.toSkip[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
                            break;
                        case MORE:
                            data.hasMoreActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.hasMore = true;
                            data.toMore[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);

                            if (data.newLexState[curRE.getOrdinal()] != null) {
                                data.canReachOnMore[data.getStateIndex(
                                        data.newLexState[curRE.getOrdinal()])] = true;
                            } else {
                                data.canReachOnMore[stateData.getStateIndex()] = true;
                            }
                            break;
                        case TOKEN:
                            data.hasTokenActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.toToken[curRE.getOrdinal() / 64] |=
                                    1L << (curRE.getOrdinal() % 64);
                            break;
                    }
                }
            }

            NfaState.ComputeClosures(stateData);

            for (int i = 0; i < stateData.getInitialState().epsilonMoves.size(); i++) {
                stateData.getInitialState().epsilonMoves.get(i).GenerateCode();
            }

            stateData.hasNFA = (stateData.generatedStates() != 0);
            if (stateData.hasNFA) {
                stateData.getInitialState().GenerateCode();
                stateData.getInitialState().GetEpsilonMovesString();
                if (stateData.getInitialState().epsilonMovesString == null) {
                    stateData.getInitialState().epsilonMovesString = "null;";
                }
                stateData.addCompositeStateSet(stateData.getInitialState().epsilonMovesString);
            }

            if ((stateData.getInitialState().kind != Integer.MAX_VALUE) && (
                    stateData.getInitialState().kind != 0)) {
                if (((data.toSkip[stateData.getInitialState().kind / 64] & (1L
                        << stateData.getInitialState().kind)) != 0L)
                        || ((data.toSpecial[stateData.getInitialState().kind / 64]
                        & (1L << stateData.getInitialState().kind)) != 0L)) {
                    data.hasSkipActions = true;
                } else if ((data.toMore[stateData.getInitialState().kind / 64]
                        & (1L << stateData.getInitialState().kind)) != 0L) {
                    data.hasMoreActions = true;
                } else {
                    data.hasTokenActions = true;
                }

                if ((data.initMatch[stateData.getStateIndex()] == 0)
                        || (data.initMatch[stateData.getStateIndex()]
                        > stateData.getInitialState().kind)) {
                    data.initMatch[stateData.getStateIndex()] = stateData.getInitialState().kind;
                    data.hasEmptyMatch = true;
                }
            } else if (data.initMatch[stateData.getStateIndex()] == 0) {
                data.initMatch[stateData.getStateIndex()] = Integer.MAX_VALUE;
            }

            StringLiteralAnalyzer.fillSubString(stateData);

            if (stateData.hasNFA && !stateData.isMixedState()) {
                generateNfaStartStates(stateData, stateData.getInitialState());
            }

            if (data.stateSetSize < stateData.generatedStates()) {
                data.stateSetSize = stateData.generatedStates();
            }
        }
    }

    /**
     * Computes NFA start state sets for string literal matching.
     */
    private static void generateNfaStartStates(NfaStateData data, NfaState initialState) {
        boolean[] seen = new boolean[data.generatedStates()];
        Map<String, String> stateSets = new LinkedHashMap<>();
        String stateSetString = "";
        int i, j, kind, jjmatchedPos = 0;
        int maxKindsReqd = (data.maxStrKind / 64) + 1;
        long[] actives;
        List<NfaState> newStates = new ArrayList<>();
        List<NfaState> oldStates = null, jjtmpStates;

        data.statesForPos = NfaStateData.newStatesForPos(data.maxLen);
        data.intermediateKinds = new int[data.maxStrKind + 1][];
        data.intermediateMatchedPos = new int[data.maxStrKind + 1][];

        // Precompute image -> smallest matching string kind for this lexical state, so the inner
        // loop below does an O(1) lookup instead of rescanning every string kind (the former
        // getStrKind was O(maxStrKind), making the whole pass O(maxStrKind^2 * maxLen)).
        Map<String, Integer> strKindByImage = new HashMap<>();
        for (int k = 0; k < data.maxStrKind; k++) {
            if (data.global.getState(k) != data.getStateIndex()) {
                continue;
            }
            String img = data.global.getImage(k);
            if (img != null) {
                strKindByImage.putIfAbsent(img, k);
            }
        }

        for (i = 0; i < data.maxStrKind; i++) {
            if (data.global.getState(i) != data.getStateIndex()) {
                continue;
            }

            String image = data.global.getImage(i);
            if ((image == null) || (image.isEmpty())) {
                continue;
            }

            oldStates = new ArrayList<>(initialState.epsilonMoves);
            if (oldStates.isEmpty()) {
                return;
            }

            // Positions count characters, not UTF-16 units (ADR-0029).
            int[] chars = image.codePoints().toArray();
            data.intermediateKinds[i] = new int[chars.length];
            data.intermediateMatchedPos[i] = new int[chars.length];
            jjmatchedPos = 0;
            kind = Integer.MAX_VALUE;

            for (j = 0; j < chars.length; j++) {
                if ((oldStates == null) || oldStates.isEmpty()) {
                    kind = data.intermediateKinds[i][j] = data.intermediateKinds[i][j - 1];
                    jjmatchedPos = data.intermediateMatchedPos[i][j] = data.intermediateMatchedPos[i][j - 1];
                } else {
                    kind = NfaState.MoveFromSet(chars[j], oldStates, newStates);
                    oldStates.clear();

                    if ((j == 0) && (kind != Integer.MAX_VALUE) && (
                            data.global.canMatchAnyChar[data.getStateIndex()] != -1)
                            && (kind > data.global.canMatchAnyChar[data.getStateIndex()])) {
                        kind = data.global.canMatchAnyChar[data.getStateIndex()];
                    }

                    if (strKindByImage.getOrDefault(image.substring(0, image.offsetByCodePoints(0, j + 1)),
                            Integer.MAX_VALUE) < kind) {
                        data.intermediateKinds[i][j] = kind = Integer.MAX_VALUE;
                        jjmatchedPos = 0;
                    } else if (kind != Integer.MAX_VALUE) {
                        data.intermediateKinds[i][j] = kind;
                        jjmatchedPos = data.intermediateMatchedPos[i][j] = j;
                    } else if (j == 0) {
                        kind = data.intermediateKinds[i][j] = Integer.MAX_VALUE;
                    } else {
                        kind = data.intermediateKinds[i][j] = data.intermediateKinds[i][j - 1];
                        jjmatchedPos = data.intermediateMatchedPos[i][j] = data.intermediateMatchedPos[i][j - 1];
                    }

                    stateSetString = epsilonMovesString(data, newStates);
                }

                if ((kind == Integer.MAX_VALUE) && ((newStates == null) || (newStates.isEmpty()))) {
                    continue;
                }

                int p;
                if (stateSets.get(stateSetString) == null) {
                    stateSets.put(stateSetString, stateSetString);
                    for (p = 0; p < newStates.size(); p++) {
                        if (seen[newStates.get(p).stateName]) {
                            newStates.get(p).inNextOf++;
                        } else {
                            seen[newStates.get(p).stateName] = true;
                        }
                    }
                } else {
                    for (p = 0; p < newStates.size(); p++) {
                        seen[newStates.get(p).stateName] = true;
                    }
                }

                jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();

                if (data.statesForPos[j] == null) {
                    data.statesForPos[j] = new Hashtable<>();
                }

                if ((actives = (data.statesForPos[j].get(
                        kind + ", " + jjmatchedPos + ", " + stateSetString))) == null) {
                    actives = new long[maxKindsReqd];
                    data.statesForPos[j].put(kind + ", " + jjmatchedPos + ", " + stateSetString,
                            actives);
                }

                actives[i / 64] |= 1L << (i % 64);
            }
        }
    }

    /** The high bytes of a code point: U+10FFFF >> 8 is 0x10FF (ADR-0029). */
    static final int HIGH_BYTES = (Character.MAX_CODE_POINT >> 8) + 1;

    /**
     * Computes non-ASCII move indices and bit vectors for a single NFA state.
     *
     * <p>A character is looked up in two steps: its high byte (bits 8 to 20) selects a 256-bit
     * vector over its low byte. High bytes whose vectors are equal share one, and the set of those
     * high bytes is itself a bit vector, {@link #HIGH_BYTES} bits wide.
     */
    static void getNonAsciiMoves(LexerData data, NfaState state) {
        if (((state.charMoves == null) || (state.charMoves[0] == 0))
                && ((state.rangeMoves == null) || (state.rangeMoves[0] == 0))) {
            return;
        }

        // Only the high bytes a move reaches get a vector.
        long[][] loBytes = new long[HIGH_BYTES][];

        if (state.charMoves != null) {
            for (int c : state.charMoves) {
                if (c == 0) {
                    break;
                }
                Nfa.setLowByte(loBytes, c >> 8, c & 0xff);
            }
        }

        if (state.rangeMoves != null) {
            for (int i = 0; i < state.rangeMoves.length; i += 2) {
                if (state.rangeMoves[i] == 0) {
                    break;
                }

                int left = state.rangeMoves[i];
                int right = state.rangeMoves[i + 1];
                int hiByte = left >> 8;

                if (hiByte == (right >> 8)) {
                    for (int c = left & 0xff; c <= (right & 0xff); c++) {
                        Nfa.setLowByte(loBytes, hiByte, c);
                    }
                    continue;
                }

                for (int c = left & 0xff; c <= 0xff; c++) {
                    Nfa.setLowByte(loBytes, hiByte, c);
                }

                while (++hiByte < (right >> 8)) {
                    loBytes[hiByte] = new long[] {-1L, -1L, -1L, -1L};
                }

                for (int c = 0; c <= (right & 0xff); c++) {
                    Nfa.setLowByte(loBytes, hiByte, c);
                }
            }
        }

        // The high bytes that share a vector, in the order of the first of each. This used to be
        // a pairwise comparison of all high bytes, which was fine for 256 of them.
        Map<BitVector, List<Integer>> groups = new LinkedHashMap<>();
        for (int hi = 0; hi < HIGH_BYTES; hi++) {
            if (loBytes[hi] != null) {
                groups.computeIfAbsent(new BitVector(loBytes[hi]), v -> new ArrayList<>()).add(hi);
            }
        }

        var indices = new ArrayList<Integer>();
        for (var group : groups.entrySet()) {
            if (group.getValue().size() > 1) {
                long[] common = new long[HIGH_BYTES / 64];
                for (int hi : group.getValue()) {
                    common[hi / 64] |= (1L << (hi % 64));
                }
                indices.add(internBitVector(data, common));
                indices.add(internBitVector(data, group.getKey().words().clone()));
            }
        }
        state.nonAsciiMoveIndices = indices.stream().mapToInt(Integer::intValue).toArray();

        for (int hi = 0; hi < HIGH_BYTES; hi++) {
            if ((loBytes[hi] != null) && (groups.get(new BitVector(loBytes[hi])).size() == 1)) {
                state.loByteVec.add(hi);
                state.loByteVec.add(internBitVector(data, loBytes[hi].clone()));
            }
        }
        updateDuplicateNonAsciiMoves(data, state);
    }

    private static void setLowByte(long[][] loBytes, int hiByte, int loByte) {
        if (loBytes[hiByte] == null) {
            loBytes[hiByte] = new long[4];
        }
        loBytes[hiByte][loByte / 64] |= (1L << (loByte % 64));
    }

    /**
     * Interns a bit vector in the shared tables and returns its index: a low-byte vector of four
     * words, or a high-byte vector of {@code HIGH_BYTES / 64}.
     */
    private static int internBitVector(LexerData data, long[] vec) {
        var key = new BitVector(vec);
        Integer ind = data.lohiByteTab.get(key);
        if (ind == null) {
            ind = data.lohiByteTab.size();
            data.allBitsSet.add(key.allBitsSet());
            if (!key.allBitsSet()) {
                data.lohiByte.put(ind, vec);
            }
            data.lohiByteTab.put(key, ind);
        }
        return ind;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String epsilonMovesString(NfaStateData data, List<NfaState> states) {
        if ((states == null) || (states.isEmpty())) {
            return "null;";
        }

        int[] set = new int[states.size()];
        var sb = new StringBuilder("{ ");
        for (int i = 0; i < states.size(); ) {
            int k;
            sb.append(k = states.get(i).stateName).append(", ");
            set[i] = k;

            if ((i++ > 0) && ((i % 16) == 0)) {
                sb.append("\n");
            }
        }

        sb.append("};");
        String epsilonMovesString = sb.toString();
        data.setNextStates(epsilonMovesString, set);
        return epsilonMovesString;
    }

    private static void updateDuplicateNonAsciiMoves(LexerData data, NfaState state) {
        for (int i = 0; i < data.nonAsciiTableForMethod.size(); i++) {
            NfaState tmp = data.nonAsciiTableForMethod.get(i);
            if (NfaState.EqualLoByteVectors(state.loByteVec, tmp.loByteVec)
                    && NfaState.EqualNonAsciiMoveIndices(state.nonAsciiMoveIndices,
                    tmp.nonAsciiMoveIndices)) {
                state.nonAsciiMethod = i;
                return;
            }
        }

        state.nonAsciiMethod = data.nonAsciiTableForMethod.size();
        data.nonAsciiTableForMethod.add(state);
    }
}
