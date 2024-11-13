// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.TemplateProvider;

import java.io.File;
import java.io.FileNotFoundException;


/**
 * The {@link JavaTemplate} class.
 */
public enum JavaTemplate implements TemplateProvider {

  LEXER("Lexer", "%s"),
  PARSER("Parser", "%s"),

  PROVIDER("Provider"),
  STREAM_PROVIDER("StreamProvider"),
  STRING_PROVIDER("StringProvider"),
  CHAR_STREAM("JavaCharStream"),

  MULTI_NODE("MultiNode", "%s"),
  NODE("Node"),

  PARSER_EXCEPTION("ParseException"),
  PARSER_CONSTANTS("ParserConstants", "%sConstants"),
  TOKEN("Token"),
  TOKEN_EXCEPTION("TokenException"),

  TREE("Tree"),
  TREE_STATE("TreeState", "JJT%sState"),
  TREE_CONSTANTS("TreeConstants", "%sTreeConstants"),

  VISITOR("Visitor", "%sVisitor"),
  DEFAULT_VISITOR("DefaultVisitor", "%sDefaultVisitor");

  private final String name;
  private final String path;

  private JavaTemplate(String name) {
    this.name = name;
    this.path = name;
  }

  private JavaTemplate(String name, String path) {
    this.name = name;
    this.path = path;
  }

  @Override
  public String getTemplate() {
    return String.format("java/%s", this.name);
  }

  @Override
  public String getFilename(String name) {
    return String.format("%s.java", (name == null) ? this.name : String.format(this.path, name));
  }

  @Override
  public final DigestWriter createDigestWriter(DigestOptions options) throws FileNotFoundException {
    return createDigestWriter(options, null);
  }

  @Override
  public final DigestWriter createDigestWriter(DigestOptions options, String name) throws FileNotFoundException {
    File file = getFile(getFilename(name), options.getOptions());
    return DigestWriter.create(file, options, false);
  }

  /**
   * Get the Java file to generate.
   *
   * @param filename
   * @param options
   */
  public static File getFile(String filename, Options options) {
    String packagePath = options.getJavaPackage().replace('.', File.separatorChar);
    File outputDir = new File(options.getOutputDirectory(), packagePath);
    outputDir.mkdirs();
    return new File(outputDir, filename);
  }
}
