// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import org.hivevm.cc.HiveCC;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

/**
 * The {@link DigestWriter} class.
 */
public class DigestWriter extends PrintWriter {

  private static final MessageDigest DIGEST = DigestWriter.createMD5();


  private final OutputStream          stream;
  private final DigestOutputStream    digest;
  private final ByteArrayOutputStream bytes;
  private final DigestOptions         options;

  private final boolean               isCpp;

  /**
   * Constructs an instance of {@link DigestWriter}.
   *
   * @param digest
   * @param stream
   * @param bytes
   */
  private DigestWriter(OutputStream stream, DigestOutputStream digest, ByteArrayOutputStream bytes, Version version,
      DigestOptions options, boolean isCpp) {
    super(digest);
    this.stream = stream;
    this.digest = digest;
    this.bytes = bytes;
    this.options = options;
    this.isCpp = isCpp;
    printf("// Generated by HiveVM CC v.%s - Do not edit this line!\n\n", version.toString("0.0"));

    if (isCpp) {
      println("// disable warnings on parser header files");
      println("#pragma GCC diagnostic push");
      println("#pragma GCC diagnostic ignored \"-Wunused-parameter\"");
      println("#pragma GCC diagnostic ignored \"-Wunused-variable\"");
      println();
    }
  }

  /**
   * Get options as wrapper.
   */
  public final DigestOptions options() {
    return this.options;
  }

  /**
   * Closes the stream and releases any system resources associated with it. Closing a previously
   * closed stream has no effect.
   */
  @Override
  public void close() {
    if (this.isCpp) {
      println();
      println("#pragma GCC diagnostic pop");
    }

    super.close();

    String checksum = DigestWriter.toChecksum(this.digest.getMessageDigest().digest());
    try (PrintWriter writer = new PrintWriter(this.stream)) {
      this.stream.write(this.bytes.toByteArray());
      writer.println();
      writer.printf("// HiveVM CC Checksum=%s (Do not edit this line!)\n", checksum);
      if (this.options.hasConsumed()) {
        writer.printf("// HiveVM CC Options: %s\n", this.options.consumed().collect(Collectors.joining(", ")));
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates an instance of MD5 {@link MessageDigest}.
   */
  private static MessageDigest createMD5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates an MD5 based checksum of the bytes.
   *
   * @param bytes
   */
  private static String toChecksum(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (byte b : bytes) {
      builder.append(String.format("%02X", b));
    }
    return builder.toString();
  }

  /**
   * Constructs an instance of {@link DigestWriter}.
   *
   * @param stream
   * @param version
   * @param options
   */
  private static DigestWriter create(OutputStream stream, Version version, DigestOptions options, boolean isCpp) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DigestOutputStream digest = new DigestOutputStream(bytes, DigestWriter.DIGEST);
    return new DigestWriter(stream, digest, bytes, version, options, isCpp);
  }

  /**
   * Constructs an instance of {@link DigestWriter}.
   *
   * @param file
   * @param options
   */
  public static DigestWriter create(File file, DigestOptions options, boolean isCpp) throws FileNotFoundException {
    return DigestWriter.create(new FileOutputStream(file), HiveCC.VERSION, options, isCpp);
  }
}
