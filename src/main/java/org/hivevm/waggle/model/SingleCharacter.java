// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/SingleCharacter.java

package org.hivevm.waggle.model;

/**
 * Describes single character descriptors in a character list.
 */
public class SingleCharacter extends Production {

    /** A Unicode code point (ADR-0029). */
    private final int ch;

    public SingleCharacter(int c) {
        this.ch = c;
    }

    public int getChar() {
        return this.ch;
    }
}
