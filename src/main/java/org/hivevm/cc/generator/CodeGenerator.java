// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.File;

import org.hivevm.cc.JJLanguage;
import org.hivevm.cc.parser.JavaCCParserConstants;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.source.SourceWriter;
import org.hivevm.cc.utils.Encoding;

class CodeGenerator {

  private int crow, ccol;

  protected final void saveOutput(SourceWriter writer, File path) {
    writer.saveOutput(path);
  }

  protected void genTokenSetup(Token t) {
    Token tt = t;

    while (tt.specialToken != null) {
      tt = tt.specialToken;
    }

    this.crow = tt.beginLine;
    this.ccol = tt.beginColumn;
  }

  protected final void resetColumn() {
    this.ccol = 1;
  }

  protected final String getLeadingComments(Token t) {
    String retval = "";
    if (t.specialToken == null) {
      return retval;
    }
    Token tt = t.specialToken;
    while (tt.specialToken != null) {
      tt = tt.specialToken;
    }
    while (tt != null) {
      retval += getStringForTokenOnly(tt);
      tt = tt.next;
    }
    if ((this.ccol != 1) && (this.crow != t.beginLine)) {
      retval += "\n";
      this.crow++;
      this.ccol = 1;
    }
    return retval;
  }

  protected final String getStringToPrint(Token t) {
    String retval = "";
    Token tt = t.specialToken;
    if (tt != null) {
      while (tt.specialToken != null) {
        tt = tt.specialToken;
      }
      while (tt != null) {
        retval += getStringForTokenOnly(tt);
        tt = tt.next;
      }
    }

    return retval + getStringForTokenOnly(t);
  }

  protected String getStringForTokenOnly(Token t) {
    String retval = "";
    for (; this.crow < t.beginLine; this.crow++) {
      retval += "\n";
      this.ccol = 1;
    }
    for (; this.ccol < t.beginColumn; this.ccol++) {
      retval += " ";
    }
    if ((t.kind == JavaCCParserConstants.STRING_LITERAL) || (t.kind == JavaCCParserConstants.CHARACTER_LITERAL)) {
      retval += Encoding.escapeUnicode(t.image);
    } else if (t.image.startsWith(JJLanguage.Java.CODE)) {
      retval += JJLanguage.Java.strip(t.image);
    } else if (t.image.startsWith(JJLanguage.Cpp.CODE)) {
      retval += JJLanguage.Cpp.strip(t.image);
    } else {
      retval += t.image;
    }
    this.crow = t.endLine;
    this.ccol = t.endColumn + 1;
    if (t.image.length() > 0) {
      char last = t.image.charAt(t.image.length() - 1);
      if ((last == '\n') || (last == '\r')) {
        this.crow++;
        this.ccol = 1;
      }
    }
    return retval;
  }
}
