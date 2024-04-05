// Copyright 2012 Google Inc. All Rights Reserved.
// Author: sreeni@google.com (Sreeni Viswanadha)

/*
 * Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer. * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. * Neither the name of the Sun Microsystems, Inc. nor
 * the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package it.smartio.fastcc.generator.java;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import it.smartio.fastcc.FastCC;
import it.smartio.fastcc.JavaCCRequest;
import it.smartio.fastcc.generator.AbstractFileGenerator;
import it.smartio.fastcc.generator.FileGenerator;
import it.smartio.fastcc.generator.LexerData;
import it.smartio.fastcc.parser.JavaCCErrors;
import it.smartio.fastcc.parser.ParseException;
import it.smartio.fastcc.parser.RStringLiteral;
import it.smartio.fastcc.parser.RegExprSpec;
import it.smartio.fastcc.parser.RegularExpression;
import it.smartio.fastcc.parser.TokenProduction;
import it.smartio.fastcc.utils.DigestOptions;
import it.smartio.fastcc.utils.DigestWriter;
import it.smartio.fastcc.utils.Encoding;

/**
 * Generates the Constants file.
 */
public class JavaFileGenerator extends AbstractFileGenerator implements FileGenerator {

  /**
   * Gets the template by name.
   * 
   * @param name
   */
  protected final String getTemplate(String name) {
    return String.format("/templates/java/%s.template", name.substring(0, name.length() - 5));
  }

  /**
   * Creates a new {@link DigestWriter}.
   * 
   * @param file
   * @param options
   */
  protected final DigestWriter createDigestWriter(File file, DigestOptions options) throws FileNotFoundException {
    DigestWriter writer = DigestWriter.create(file, FastCC.VERSION, options);
    writer.println("package " + options.getOptions().getJavaPackage() + ";");
    writer.println();
    return writer;
  }

  @Override
  public final void handleRequest(JavaCCRequest request, LexerData context) throws ParseException {
    if (JavaCCErrors.hasError()) {
      throw new ParseException();
    }

    handleToken(request, context);
    handleProvider(request, context);
    handleException(request, context);

    handleParserConstants(request, context);
  }

  protected final void handleToken(JavaCCRequest request, LexerData context) throws ParseException {
    generateFile("Token.java", context);
    generateFile("TokenException.java", context);
  }

  protected final void handleProvider(JavaCCRequest request, LexerData context) throws ParseException {
    generateFile("Provider.java", context);
    generateFile("StringProvider.java", context);
    generateFile("StreamProvider.java", context);
    generateFile("JavaCharStream.java", context);
  }

  protected final void handleException(JavaCCRequest request, LexerData context) throws ParseException {
    generateFile("ParseException.java", context);
  }

  protected final void handleParserConstants(JavaCCRequest request, LexerData context) throws ParseException {
    List<RegularExpression> expressions = new ArrayList<>();
    for (TokenProduction tp : request.getTokenProductions()) {
      for (RegExprSpec res : tp.getRespecs()) {
        expressions.add(res.rexp);
      }
    }
    
    DigestOptions options = new DigestOptions(context.options());
    options.addValues("STATES", context.getStateCount()).add(i -> String.valueOf(i)).add(i -> context.getStateName(i));
    options.addValues("TOKENS", request.getOrderedsTokens()).add(r -> "" + r.getOrdinal()).add(r -> r.getLabel());
    options.addValues("PRODUCTIONS", expressions).add(re -> {
      StringBuffer buffer = new StringBuffer();

      if (re instanceof RStringLiteral) {
        buffer.append("\"\\\"" + Encoding.escape(Encoding.escape(((RStringLiteral) re).getImage())) + "\\\"\"");
      } else if (!re.getLabel().equals("")) {
        buffer.append("\"<" + re.getLabel() + ">\"");
      } else if (re.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
        JavaCCErrors.warning(re, "Consider giving this non-string token a label for better error reporting.");
      } else {
        buffer.append("\"<token of kind " + re.getOrdinal() + ">\"");
      }

      if (expressions.indexOf(re) < expressions.size() - 1)
        buffer.append(",");
      return buffer.toString();
    });

    generateFile("ParserConstants.java", request.getParserName() + "Constants.java", options);
  }
}
