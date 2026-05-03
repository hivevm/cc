// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.hivevm.cc.parser.Token;

/**
 * Describes BNF productions.
 */

public class BNFProduction extends NormalProduction {

    private final List<Token> declaration_tokens;
    private final List<Token> end_tokens;

    private       int                           nextScope;
    private       boolean                       non_terminal;
    private       boolean                       is_action;
    private final Hashtable<NodeScope, Integer> scopes;

    public BNFProduction() {
        this.declaration_tokens = new ArrayList<>();
        this.end_tokens = new ArrayList<>();
        this.nextScope = 0;
        this.scopes = new Hashtable<>();
    }

    public BNFProduction addDeclaration(Token token) {
        this.declaration_tokens.add(token);
        return this;
    }

    public BNFProduction addDeclarationEnd(Token token) {
        this.end_tokens.add(token);
        return this;
    }

    public List<Token> getDeclarationTokens() {
        return this.declaration_tokens;
    }

    public List<Token> getDeclarationEndTokens() {
        return this.end_tokens;
    }

    public int getNodeScopeNumber(NodeScope s) {
        Integer i = this.scopes.get(s);
        if (i == null) {
            i = this.nextScope++;
            this.scopes.put(s, i);
        }
        return i;
    }

    public boolean isNonTerminal() {
        return this.non_terminal;
    }

    public void setNonTerminal() {
        this.non_terminal = true;
    }

    public boolean isAction() {
        return this.is_action;
    }

    public void setIsAction() {
        this.is_action = true;
    }
}
