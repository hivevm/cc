// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import org.jspecify.annotations.NonNull;

public interface SourceWriter {

    //void write(@NonNull String s);
    void println(@NonNull String s);

    SourceWriter append(@NonNull String text);

    default SourceWriter new_line() {
        return append("\n");
    }
}
