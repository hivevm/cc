// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.hivevm.cc.JJLanguage;
import org.hivevm.cc.utils.Encoding;


/**
 * The {@link JJTreeWriter} class.
 */
public class JJTreeWriter extends PrintWriter {

  private static final String SELF   = "SELF";
  private static final String JJTREE = "jjtree";


  private final JJLanguage language;


  // Indicates whether the token should be replaced by white space or replaced with the actual node
  // variable.
  private boolean whitingOut = false;

  /**
   * Constructs an instance of {@link JJTreeWriter}.
   *
   * @param file
   * @param language
   */
  public JJTreeWriter(File file, JJLanguage language) throws FileNotFoundException {
    super(file);
    this.language = language;
  }

  /**
   * Get the current {@link JJLanguage}.
   */
  public final JJLanguage getLanguage() {
    return language;
  }

  /**
   * Opens a JJTree code block.
   */
  public final void openCodeBlock(String arg) {
    append("\n");
    append(getLanguage().CODE);
    if (arg != null) {
      print(" // " + arg + "\n");
    }
  }

  /**
   * Closes a JJTree code block.
   */
  public final void closeCodeBlock() {
    append("@end");
  }

  /**
   * Prints the token for the node
   *
   * @param node
   * @param token
   */
  public final void printToken(JJTreeNode node, Token token) {
    Token tt = token.specialToken;
    if (tt != null) {
      while (tt.specialToken != null) {
        tt = tt.specialToken;
      }
      while (tt != null) {
        print(Encoding.escapeUnicode(node.translateImage(tt)));
        tt = tt.next;
      }
    }

    /*
     * If we're within a node scope we modify the source in the following ways:
     *
     * 1) we rename all references to `SELF' to be references to the actual node variable. 2) we
     * replace all calls to `jjtree.currentNode()' with references to the node variable.
     */

    NodeScope s = NodeScope.getEnclosingNodeScope(node);
    if (s == null) {
      /*
       * Not within a node scope so we don't need to modify the source.
       */
      print(Encoding.escapeUnicode(node.translateImage(token)));
      return;
    }

    if (token.image.contains(SELF)) {
      String text = Encoding.escapeUnicode(node.translateImage(token));
      print(text.replace(SELF, s.getNodeVariable()));
      return;
    }
    if (this.whitingOut) {
      if (token.image.equals(JJTREE)) {
        print(s.getNodeVariable());
        print(" ");
      } else if (token.image.equals(")")) {
        print(" ");
        this.whitingOut = false;
      } else {
        for (int i = 0; i < token.image.length(); ++i) {
          print(" ");
        }
      }
      return;
    }
    print(Encoding.escapeUnicode(node.translateImage(token)));
  }

  /**
   * This method prints the tokens corresponding to this node recursively calling the print methods
   * of its children. Overriding this print method in appropriate nodes gives the output the added
   * stuff not in the input.
   *
   * @param node
   * @param visitor
   */
  public final Object handleJJTreeNode(JJTreeNode node, JJTreeParserVisitor visitor) {
    if (node.getLastToken().next == node.getFirstToken()) {
      return null;
    }

    Token tokenFirst = node.getFirstToken();
    Token token = new Token();
    token.next = tokenFirst;

    JJTreeNode n = null;
    Object end = null;
    for (int ord = 0; ord < node.jjtGetNumChildren(); ord++) {
      n = (JJTreeNode) node.jjtGetChild(ord);
      while (true) {
        token = token.next;
        if (token == n.getFirstToken()) {
          break;
        }
        printToken(node, token);
      }
      end = n.jjtAccept(visitor, this);
      token = n.getLastToken();
    }
    if (end == null) {
      while (token != node.getLastToken()) {
        token = token.next;
        printToken(node, token);
      }
    }
    return null;
  }
}
