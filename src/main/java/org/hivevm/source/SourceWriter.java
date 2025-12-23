// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.jspecify.annotations.NonNull;

public interface SourceWriter {

    SourceWriter append(@NonNull String text);

    default SourceWriter new_line() {
        return append("\n");
    }
}
