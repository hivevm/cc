/* Copyright (c) 2006, Sun Microsystems, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */


package it.smartio.fastcc.jjdoc;

import java.io.PrintWriter;

import it.smartio.fastcc.parser.Expansion;
import it.smartio.fastcc.parser.NonTerminal;
import it.smartio.fastcc.parser.NormalProduction;
import it.smartio.fastcc.parser.RegularExpression;
import it.smartio.fastcc.parser.TokenProduction;

/**
 * Output BNF in text format.
 */
class TextGenerator implements Generator {

  protected PrintWriter ostr;

  public TextGenerator() {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#text(java.lang.String)
   */
  @Override
  public void text(String s) {
    print(s);
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#print(java.lang.String)
   */
  @Override
  public void print(String s) {
    this.ostr.print(s);
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#documentStart()
   */
  @Override
  public void documentStart() {
    this.ostr = create_output_stream();
    this.ostr.print("\nDOCUMENT START\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#documentEnd()
   */
  @Override
  public void documentEnd() {
    this.ostr.print("\nDOCUMENT END\n");
    this.ostr.close();
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#specialTokens(java.lang.String)
   */
  @Override
  public void specialTokens(String s) {
    this.ostr.print(s);
  }


  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#nonterminalsStart()
   */
  @Override
  public void nonterminalsStart() {
    text("NON-TERMINALS\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#nonterminalsEnd()
   */
  @Override
  public void nonterminalsEnd() {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#tokensStart()
   */
  @Override
  public void tokensStart() {
    text("TOKENS\n");
  }

  @Override
  public void handleTokenProduction(TokenProduction tp) {
    String text = JJDoc.getStandardTokenProductionText(tp);
    text(text);
  }


  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#tokensEnd()
   */
  @Override
  public void tokensEnd() {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#productionStart(it.smartio.fastcc.parser.NormalProduction)
   */
  @Override
  public void productionStart(NormalProduction np) {
    this.ostr.print("\t" + np.getLhs() + "\t:=\t");
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#productionEnd(it.smartio.fastcc.parser.NormalProduction)
   */
  @Override
  public void productionEnd(NormalProduction np) {
    this.ostr.print("\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#expansionStart(it.smartio.fastcc.parser.Expansion, boolean)
   */
  @Override
  public void expansionStart(Expansion e, boolean first) {
    if (!first) {
      this.ostr.print("\n\t\t|\t");
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#expansionEnd(it.smartio.fastcc.parser.Expansion, boolean)
   */
  @Override
  public void expansionEnd(Expansion e, boolean first) {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#nonTerminalStart(it.smartio.fastcc.parser.NonTerminal)
   */
  @Override
  public void nonTerminalStart(NonTerminal nt) {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#nonTerminalEnd(it.smartio.fastcc.parser.NonTerminal)
   */
  @Override
  public void nonTerminalEnd(NonTerminal nt) {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#reStart(it.smartio.fastcc.parser.RegularExpression)
   */
  @Override
  public void reStart(RegularExpression r) {}

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#reEnd(it.smartio.fastcc.parser.RegularExpression)
   */
  @Override
  public void reEnd(RegularExpression r) {}

  /**
   * Create an output stream for the generated Jack code. Try to open a file based on the name of
   * the parser, but if that fails use the standard output stream.
   */
  protected PrintWriter create_output_stream() {

    if (JJDocOptions.getOutputFile().equals("")) {
      if (JJDocGlobals.input_file.equals("standard input")) {
        return new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
      } else {
        String ext = ".html";

        if (JJDocOptions.getText()) {
          ext = ".txt";
        } else if (JJDocOptions.getXText()) {
          ext = ".xtext";
        }

        int i = JJDocGlobals.input_file.lastIndexOf('.');
        if (i == -1) {
          JJDocGlobals.output_file = JJDocGlobals.input_file + ext;
        } else {
          String suffix = JJDocGlobals.input_file.substring(i);
          if (suffix.equals(ext)) {
            JJDocGlobals.output_file = JJDocGlobals.input_file + ext;
          } else {
            JJDocGlobals.output_file = JJDocGlobals.input_file.substring(0, i) + ext;
          }
        }
      }
    } else {
      JJDocGlobals.output_file = JJDocOptions.getOutputFile();
    }

    try {
      this.ostr = new java.io.PrintWriter(new java.io.FileWriter(JJDocGlobals.output_file));
    } catch (java.io.IOException e) {
      error("JJDoc: can't open output stream on file " + JJDocGlobals.output_file + ".  Using standard output.");
      this.ostr = new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
    }

    return this.ostr;
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#debug(java.lang.String)
   */
  @Override
  public void debug(String message) {
    System.err.println(message);
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#info(java.lang.String)
   */
  @Override
  public void info(String message) {
    System.err.println(message);
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#warn(java.lang.String)
   */
  @Override
  public void warn(String message) {
    System.err.println(message);
  }

  /**
   * {@inheritDoc}
   *
   * @see it.smartio.fastcc.jjdoc.Generator#error(java.lang.String)
   */
  @Override
  public void error(String message) {
    System.err.println(message);
  }


}
