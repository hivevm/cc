// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import java.util.Arrays;

/**
 * A 256-bit set over the low byte of a character, as the lexer tables store it: four words.
 *
 * <p>Equal vectors are stored once. They used to be told apart by their Java array-initialiser
 * text, which made stage 4 format target source to answer a question about four longs.
 */
record BitVector(long[] words) {

    boolean allBitsSet() {
        return Arrays.stream(this.words).allMatch(word -> word == -1L);
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof BitVector vector) && Arrays.equals(this.words, vector.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.words);
    }

    @Override
    public String toString() {
        return Arrays.toString(this.words);
    }
}
