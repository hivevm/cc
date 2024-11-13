// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.ASTCodeGenerator;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaTreeGenerator extends ASTCodeGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter io, String indent, JJTreeOptions options) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = options.getNodeClass().isEmpty() || options.getMulti();
    String nodeClass = isType ? type : options.getNodeClass();

    addType(type);

    io.print(indent + nodeClass + " " + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      io.println(
          "(" + nodeClass + ")" + options.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else {
      io.println("new " + nodeClass + "(this, " + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      io.println(indent + "boolean " + ns.closedVar + " = true;");
    }
    io.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (options.getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (options.getTrackTokens()) {
      io.println(indent + ns.nodeVar + ".jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, String indent, boolean isFinal,
      JJTreeOptions options) {
    String closeNode = ns.getNodeDescriptor().closeNode(ns.nodeVar);
    writer.println(indent + closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      writer.println(indent + ns.closedVar + " = false;");
    }
    if (options.getNodeScopeHook()) {
      writer.println(indent + "if (jjtree.nodeCreated()) {");
      writer.println(indent + " jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      writer.println(indent + "}");
    }

    if (options.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + ".jjtSetLastToken(getToken(0));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter io, Enumeration<String> thrown_names, String indent) {
    if (thrown_names.hasMoreElements()) {
      io.println(indent + "} catch (Throwable " + ns.exceptionVar + ") {");

      if (ns.usesCloseNodeVar()) {
        io.println(indent + "  if (" + ns.closedVar + ") {");
        io.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
        io.println(indent + "    " + ns.closedVar + " = false;");
        io.println(indent + "  } else {");
        io.println(indent + "    jjtree.popNode();");
        io.println(indent + "  }");
      }

      String thrown = null;
      while (thrown_names.hasMoreElements()) {
        thrown = thrown_names.nextElement();
        io.println(indent + "  if (" + ns.exceptionVar + " instanceof " + thrown + ") {");
        io.println(indent + "    throw (" + thrown + ")" + ns.exceptionVar + ";");
        io.println(indent + "  }");
      }
      // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
      // otherwise we want to force the user to declare it by crashing on the bad cast.
      io.println(indent + "  throw (Error)" + ns.exceptionVar + ";");
    }
  }

  @Override
  protected final void catchExpansionUnit(NodeScope ns, ASTWriter writer, String indent, ASTNode expansion_unit) {
    writer.openCodeBlock(null);

    Hashtable<String, String> thrown_set = new Hashtable<>();
    findThrown(ns, thrown_set, expansion_unit);
    Enumeration<String> thrown_names = thrown_set.elements();
    insertCatchBlocks(ns, writer, thrown_names, indent);

    writer.println(indent + "} finally {");
    if (ns.usesCloseNodeVar()) {
      writer.println(indent + "  if (" + ns.closedVar + ") {");
      insertCloseNodeCode(ns, writer, indent + "    ", true, expansion_unit.jjtOptions());
      writer.println(indent + "  }");
    }
    writer.print(indent + "}");
    writer.closeCodeBlock();
  }

  @Override
  public final void generateJJTree(JJTreeOptions o) {
    generateTreeConstants(o);
    generateVisitors(o);
    generateDefaultVisitors(o);
    generateTreeState(o);

    // TreeClasses
    generateNodeClass(o);
    generateTreeClass(o);
    generateTreeClasses(o);
  }

  private void generateTreeConstants(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames());
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));

    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE_CONSTANTS;
    provider.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    Stream<String> nodes = ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void"));
    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodes.collect(Collectors.toList()));
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    options.set("RETURN_TYPE", o.getVisitorReturnType());
    options.set("ARGUMENT_TYPE", o.getVisitorDataType().equals("") ? "Object" : o.getVisitorDataType());
    options.set("EXCEPTION", JavaTreeGenerator.mergeVisitorException(o));
    options.set("IS_MULTI", o.getMulti());
    options.set("NODE_PREFIX", o.getNodePrefix());

    TemplateProvider provider = JavaTemplate.VISITOR;
    provider.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateDefaultVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    String argumentType = o.getVisitorDataType().equals("") ? "Object" : o.getVisitorDataType().trim();
    String returnValue = JavaTreeGenerator.returnValue(o.getVisitorReturnType(), argumentType);
    boolean isVoidReturnType = "void".equals(o.getVisitorReturnType());

    Stream<String> nodes = ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void"));
    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodes.collect(Collectors.toList()));
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    options.set("RETURN_TYPE", o.getVisitorReturnType());
    options.set("RETURN_VALUE", returnValue);
    options.set("RETURN", isVoidReturnType ? "" : "return ");
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("EXCEPTION", JavaTreeGenerator.mergeVisitorException(o));
    options.set("IS_MULTI", o.getMulti());
    options.set("NODE_PREFIX", o.getNodePrefix());

    TemplateProvider provider = JavaTemplate.DEFAULT_VISITOR;
    provider.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateTreeState(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE_STATE;
    provider.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateTreeClass(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE;
    provider.render(o, options);
  }

  private void generateNodeClass(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    TemplateProvider provider = JavaTemplate.NODE;
    provider.render(o, options);
  }

  private void generateTreeClasses(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, Boolean.valueOf(o.getVisitorReturnType().equals("void")));

    Set<String> excludes = o.getExcudeNodes();
    for (String nodeType : nodesToGenerate()) {
      if (!o.getBuildNodeFiles() || excludes.contains(nodeType)) {
        continue;
      }

      options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

      TemplateProvider provider = JavaTemplate.MULTI_NODE;
      provider.render(o, options, nodeType);
    }
  }

  private static String mergeVisitorException(JJTreeOptions o) {
    String ve = o.getVisitorException();
    return "".equals(ve) ? ve : " throws " + ve;
  }

  private static String returnValue(String returnType, String argumentType) {
    boolean isVoidReturnType = "void".equals(returnType);
    if (isVoidReturnType) {
      return "";
    }

    if (returnType.equals(argumentType)) {
      return " data";
    }

    switch (returnType) {
      case "boolean":
        return " false";
      case "int":
        return " 0";
      case "long":
        return " 0L";
      case "double":
        return " 0.0d";
      case "float":
        return " 0.0f";
      case "short":
        return " 0";
      case "byte":
        return " 0";
      case "char":
        return " '\u0000'";
      default:
        return " null";
    }
  }
}
