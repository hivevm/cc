// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.io.File;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.parser.Options;


/**
 * The {@link RustTemplate} class.
 */
enum RustTemplate implements TemplateProvider {

    LEXER("Lexer", "%sTokenManager"),
    PARSER("Parser", "%s"),

    PROVIDER("Provider"),
    STRING_PROVIDER("StringProvider"),
    CHAR_STREAM("JavaCharStream"),

    MULTI_NODE("MultiNode", "%s"),
    NODE("Node"),

    PARSER_EXCEPTION("ParseException"),
    PARSER_CONSTANTS("ParserConstants"),
    TOKEN("Token"),
    TOKEN_EXCEPTION("TokenException"),

    TREE_STATE("TreeState"),
    TREE_CONSTANTS("TreeConstants"),

    VISITOR("Visitor", "%sVisitor"),
    DEFAULT_VISITOR("DefaultVisitor", "%sDefaultVisitor");

    private final String name;
    private final String path;

    RustTemplate(String name) {
        this.name = name;
        this.path = name;
    }

    RustTemplate(String name, String path) {
        this.name = name;
        this.path = path;
    }

    @Override
    public String getTemplate() {
        return String.format("rust/%s", this.name);
    }

    @Override
    public String getFilename(String name) {
        return String.format("%s.rs", (name == null)
            ? this.name
            : String.format(this.path, name)
        ).toLowerCase();
    }

    @Override
    public final File getFile(Options options) {
        return getFile(options, null);
    }

    @Override
    public final File getFile(Options options, String name) {
        return RustTemplate.getFile(getFilename(name), options);
    }

    /**
     * Get the Java file to generate.
     */
    public static File getFile(String filename, Options options) {
        var target = new File(options.getOutputDirectory(), options.getParserName().toLowerCase());
        target.mkdirs();
        return new File(target, filename);
    }
}
