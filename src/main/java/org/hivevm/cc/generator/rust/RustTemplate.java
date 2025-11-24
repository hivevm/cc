// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.io.File;
import org.hivevm.cc.parser.Options;
import org.hivevm.core.TemplateProvider;

/**
 * Represents a collection of predefined templates for generating Rust code.
 * Each enum constant corresponds to a specific type of template file with an
 * associated name and optional path format for filename generation.
 *
 * Implements the {@link TemplateProvider} interface to provide mechanisms
 * for retrieving template resource paths, generating filenames, and creating
 * corresponding {@link File} objects based on user-defined options.
 */
enum RustTemplate implements TemplateProvider {

    LEXER("Lexer"),
    PARSER("Parser"),

    CHAR_STREAM("CharStream"),

    NODE("Node"),
    MULTI_NODE("MultiNode", "%s"),

    PARSER_CONSTANTS("ParserConstants"),
    TOKEN("Token"),

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
    public final String getName() {
        return this.name;
    }

    @Override
    public final String getType() {
        return "rust";
    }

    @Override
    public final String getTarget(String name) {
        var filename = name == null ? this.name : String.format(this.path, name);
        return String.format("%s.rs", filename).toLowerCase();
    }

    @Override
    public final File getTargetFile(String name, Options options) {
        var filename = getTarget(name);
        var target = new File(options.getOutputDirectory(), options.getParserName().toLowerCase());
        return new File(target, filename);
    }
}
