// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.Lookahead;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.Options;

/**
 * These lists are used to maintain expansions for which code generation in phase 2 and phase 3 is
 * required. Whenever a call is generated to a phase 2 or phase 3 routine, a corresponding entry is
 * added here if it has not already been added. The phase 3 routines have been optimized in version
 * 0.7pre2. Essentially only those methods (and only those portions of these methods) are generated
 * that are required. The lookahead amount is used to determine this. This change requires the use
 * of a hash table because it is now possible for the same phase 3 routine to be requested multiple
 * times with different lookaheads. The hash table provides a easily searchable capability to
 * determine the previous requests. The phase 3 routines now are performed in a two step process -
 * the first step gathers the requests (replacing requests with lower lookaheads with those
 * requiring larger lookaheads). The second step then generates these methods. This optimization and
 * the hashtable makes it look like we do not need the flag "phase3done" any more. But this has not
 * been removed yet.
 */
public class ParserData {

  private final JavaCCRequest request;


  // maskindex, jj2index, maskVals are variables that are shared between ParseEngine and ParseGen.
  private int                               jj2index;
  private boolean                           lookaheadNeeded;

  private final List<int[]>                 maskVals;
  private final Map<Expansion, Lookahead[]> lookaheads;
  private final Map<Lookahead, Integer>     lookaheadIndex;

  /**
   * An array used to store the first sets generated by the following method. A true entry means
   * that the corresponding token is in the first set.
   */
  private final List<Lookahead>             phase2list;
  final List<Phase3Data>                    phase3list  = new ArrayList<>();
  final Hashtable<Expansion, Integer>       phase3table = new Hashtable<>();

  /**
   * Constructs an instance of {@link ParserData}.
   *
   * @param request
   */
  ParserData(JavaCCRequest request) {
    this.request = request;

    this.jj2index = 0;
    this.lookaheadNeeded = false;
    this.maskVals = new ArrayList<>();
    this.phase2list = new ArrayList<>();
    this.lookaheads = new HashMap<>();
    this.lookaheadIndex = new HashMap<>();
  }

  public final Options options() {
    return this.request.options();
  }

  public final String getParserName() {
    return this.request.getParserName();
  }

  public final boolean isGenerated() {
    return this.request.isGenerated();
  }

  public final int getTokenCount() {
    return this.request.getTokenCount();
  }

  public final String getNameOfToken(int index) {
    return this.request.getNameOfToken(index);
  }

  public final Iterable<NormalProduction> getProductions() {
    return this.request.getNormalProductions();
  }

  public final NormalProduction getProduction(String name) {
    return this.request.getProductionTable(name);
  }

  public final int maskIndex() {
    return this.maskVals.size();
  }

  public final List<int[]> maskVals() {
    return this.maskVals;
  }

  public final boolean isLookAheadNeeded() {
    return this.lookaheadNeeded;
  }

  public final Iterable<Lookahead> getLoakaheads() {
    return this.phase2list;
  }

  public final Iterable<Expansion> getExpansions() {
    return this.phase3table.keySet();
  }

  public final int getCount(Expansion e) {
    return this.phase3table.get(e);
  }

  public final Lookahead[] getLoakaheads(Expansion e) {
    return this.lookaheads.get(e);
  }

  public final int getIndex(Lookahead lookahead) {
    return this.lookaheadIndex.get(lookahead);
  }

  public final int jj2Index() {
    return this.jj2index;
  }

  protected final void addMask(int[] maskVal, Lookahead lookahead) {
    this.lookaheadIndex.put(lookahead, maskIndex());
    this.maskVals.add(maskVal);
  }

  protected final int addLookupAhead(Lookahead lookahead) {
    this.phase2list.add(lookahead);
    return ++this.jj2index;
  }

  protected final void setLookupAhead(Expansion e, Lookahead[] lookaheads) {
    this.lookaheads.put(e, lookaheads);
  }


  protected final void addExpansion(Lookahead la) {
    Expansion e = la.getLaExpansion();
    Phase3Data p3d = new Phase3Data(e, la.getAmount());
    this.phase3list.add(p3d);
    this.phase3table.put(e, la.getAmount());
  }

  protected final void setLookAheadNeeded(boolean lookaheadNeeded) {
    this.lookaheadNeeded = lookaheadNeeded;
  }


  /**
   * This class stores information to pass from phase 2 to phase 3.
   */
  class Phase3Data {

    // This is the expansion to generate the jj3 method for.
    final Expansion exp;

    // This is the number of tokens that can still be consumed. This number is used to limit the
    // number of jj3 methods generated.
    final int count;

    Phase3Data(Expansion e, int c) {
      this.exp = e;
      this.count = c;
    }
  }
}
