
package org.hivevm.cc;

import java.io.File;

import org.hivevm.cc.HiveCCBuilder.Language;
import org.junit.jupiter.api.Test;


class CCParserTest {

  public static final File ROOT          = new File("/data/smartIO/fastcc");
  public static final File PARSER_SOURCE = new File(CCParserTest.ROOT, "src/main/resources");
  public static final File PARSER_TARGET = new File(CCParserTest.ROOT, "src/main/gen/it/smartio/fastcc");

  @Test
  void testJJParser() {
    HiveCCBuilder builder = HiveCCBuilder.of(Language.Java);
    builder.setTargetDir(new File(CCParserTest.PARSER_TARGET, "parser"));
    builder.setJJFile(CCParserTest.PARSER_SOURCE, "JavaCC.jj");
    builder.build();
  }

  @Test
  void testJJTree() {
    HiveCCBuilder builder = HiveCCBuilder.of(Language.Java);
    builder.setTargetDir(new File(CCParserTest.PARSER_TARGET, "jjtree"));
    builder.setJJTreeFile(CCParserTest.PARSER_SOURCE, "JJTree.jjt");
    builder.build();
  }
}
