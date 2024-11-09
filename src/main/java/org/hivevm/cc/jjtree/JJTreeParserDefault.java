// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.io.Reader;

/**
 * The {@link JJTreeParserDefault} implements a parser for the .jjt files.
 */
public class JJTreeParserDefault extends JJTreeParser {

  private final JJTreeOptions options;

  public JJTreeParserDefault(Reader reader, JJTreeOptions options) {
    super(new JJTreeParserTokenManager(new JavaCharStream(new StreamProvider(reader))));
    this.options = options;
  }

  /**
   * Parses the {@link Reader} and creates the abstract syntax tree.
   */
  public final ASTGrammar parse() throws ParseException {
    javacc_input();
    return (ASTGrammar) this.jjtree.rootNode();
  }

  @Override
  protected final void jjtreeOpenNodeScope(Node n) {
    ((JJTreeNode) n).setFirstToken(getToken(1));
  }

  @Override
  protected final void jjtreeCloseNodeScope(Node n) {
    ((JJTreeNode) n).setLastToken(getToken(0));
  }

  @Override
  protected final JJTreeOptions getOptions() {
    return this.options;
  }
}
