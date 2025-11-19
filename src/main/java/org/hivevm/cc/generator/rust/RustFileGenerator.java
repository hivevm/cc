// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.TemplateProvider;

/**
 * Generates the Constants file.
 */
class RustFileGenerator implements FileGenerator {

    @Override
    public final void generate(LexerData context) {
        TemplateProvider.render(RustTemplate.TOKEN, context.options());
        TemplateProvider.render(RustTemplate.TOKEN_EXCEPTION, context.options());

        TemplateProvider.render(RustTemplate.PROVIDER, context.options());
        TemplateProvider.render(RustTemplate.STRING_PROVIDER, context.options());
        TemplateProvider.render(RustTemplate.CHAR_STREAM, context.options());

        TemplateProvider.render(RustTemplate.PARSER_EXCEPTION, context.options());
    }
}
