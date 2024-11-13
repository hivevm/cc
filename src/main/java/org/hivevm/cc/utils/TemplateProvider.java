// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * The {@link TemplateProvider} class.
 */
public interface TemplateProvider {

  String getTemplate();

  String getFilename(String name);

  DigestWriter createDigestWriter(DigestOptions options) throws FileNotFoundException;

  DigestWriter createDigestWriter(DigestOptions options, String filename) throws FileNotFoundException;

  default void render(Options options, TemplateOptions options2) {
    DigestOptions digest = new DigestOptions(options, options2);
    try (DigestWriter writer = createDigestWriter(digest)) {
      Template.of(this, digest).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  default void render(Options options, TemplateOptions options2, String filename) {
    DigestOptions digest = new DigestOptions(options, options2);
    try (DigestWriter writer = createDigestWriter(digest, filename)) {
      Template.of(this, digest).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param template
   * @param options
   */
  static void render(TemplateProvider template, Options options) {
    generate(template, null, new DigestOptions(options));
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param template
   * @param options
   */
  static void render(TemplateProvider template, Options options, TemplateOptions options2, String name) {
    generate(template, name, new DigestOptions(options, options2));
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param tpl
   * @param name
   * @param options
   */
  static void generate(TemplateProvider tpl, String name, DigestOptions options) {
    try (DigestWriter writer = tpl.createDigestWriter(options, name)) {
      Template template = Template.of(tpl, writer.options());
      template.render(writer);
    } catch (IOException e) {
      String filename = tpl.getFilename(name);
      System.err.println("Failed to create file: " + filename + " " + e);
      JavaCCErrors.semantic_error("Could not open file: " + filename + " for writing.");
      throw new Error();
    }
  }
}
