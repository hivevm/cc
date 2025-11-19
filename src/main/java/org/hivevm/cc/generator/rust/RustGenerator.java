// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.generator.TreeGenerator;

/**
 * The {@link RustGenerator} class.
 */
@GeneratorName("Rust")
public class RustGenerator extends GeneratorProvider {

    @Override
    protected final TreeGenerator newASTGenerator() {
        return new RustASTGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return new RustFileGenerator();
    }

    @Override
    protected final LexerGenerator newLexerGenerator() {
        return new RustLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new RustParserGenerator();
    }
}
