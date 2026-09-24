// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/CharacterRange.java

package org.hivevm.waggle.model;

/**
 * Describes character range descriptors in a character list.
 */

public class CharacterRange extends Production {

    /**
     * The leftmost and the rightmost characters in this character range, as Unicode code points
     * (ADR-0029).
     */
    private int right;

    private int left;

    // An inverted user range (left > right) is caught and reported earlier, in the parser layer
    // (AbstractGrammarParser#character_descriptor_assign clamps the right end); ranges built here are
    // always well-formed, so the model performs no reporting of its own (ADR-0013).
    public CharacterRange(int l, int r) {
        setLeft(l);
        setRight(r);
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getLeft() {
        return this.left;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getRight() {
        return this.right;
    }
}
