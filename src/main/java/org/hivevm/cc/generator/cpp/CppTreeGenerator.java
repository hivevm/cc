// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.JJTreeCodeGenerator;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.TemplateOptions;

import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CppTreeGenerator extends JJTreeCodeGenerator {

  @Override
  protected final String getPointer() {
    return "->";
  }

  @Override
  protected final String getBoolean() {
    return "bool";
  }

  @Override
  protected String getTryFinally() {
    return "";
  }

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter io, String indent, JJTreeOptions options) {
    String type = ns.getNodeDescriptor().getNodeType();
    final String nodeClass;
    if ((options.getNodeClass().length() > 0) && !options.getMulti()) {
      nodeClass = options.getNodeClass();
    } else {
      nodeClass = type;
    }

    addType(type);

    io.print(indent + nodeClass + " *" + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      io.println("(" + nodeClass + "*)" + options.getNodeFactory() + "->jjtCreate(" + ns.getNodeDescriptor().getNodeId()
          + ");");
    } else {
      io.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      io.println(indent + getBoolean() + " " + ns.closedVar + " = true;");
    }
    io.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (options.getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (options.getTrackTokens()) {
      io.println(indent + ns.nodeVar + getPointer() + "jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter io, Enumeration<String> thrown_names, String indent) {
    io.println(indent + "} catch (...) {"); // " + ns.exceptionVar + ") {");

    if (ns.usesCloseNodeVar()) {
      io.println(indent + "  if (" + ns.closedVar + ") {");
      io.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
      io.println(indent + "    " + ns.closedVar + " = false;");
      io.println(indent + "  } else {");
      io.println(indent + "    jjtree.popNode();");
      io.println(indent + "  }");
    }
  }

  @Override
  public final void generateJJTree(JJTreeOptions o) {
    generateTreeConstants(o);
    generateVisitors(o);
    generateTreeState(o);

    // TreeClasses
    generateNodeHeader(o);
    generateNodeImpl(o);
    generateMultiTreeImpl(o);
    generateOneTreeInterface(o);
    // generateOneTreeImpl();
    generateTreeInterface(o);
  }

  private void generateTreeState(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);

    CppTemplate template = CppTemplate.TREESTATE_H;
    template.render(o, options);

    template = CppTemplate.TREESTATE;
    template.render(o, options);
  }

  private void generateNodeHeader(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));

    CppTemplate template = CppTemplate.NODE_H;
    template.render(o, optionMap);
  }

  private void generateTreeInterface(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));
    optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

    CppTemplate template = CppTemplate.TREE;
    template.render(o, optionMap);
  }

  private void generateNodeImpl(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));

    CppTemplate template = CppTemplate.NODE;
    template.render(o, optionMap);
  }

  private void generateMultiTreeImpl(JJTreeOptions o) {
    Set<String> excludes = o.getExcudeNodes();
    for (String node : nodesToGenerate()) {
      if (excludes.contains(node)) {
        continue;
      }

      TemplateOptions optionMap = new TemplateOptions();
      optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
          Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));
      optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

      CppTemplate template = CppTemplate.MULTINODE;
      template.render(o, optionMap);
    }
  }


  private void generateOneTreeInterface(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));
    optionMap.set("NODES", nodesToGenerate());

    CppTemplate template = CppTemplate.TREE_ONE;
    template.render(o, optionMap, JJTreeGlobals.parserName);
  }

  private void generateTreeConstants(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames().size()).set("ordinal", i -> i)
        .set("label", i -> ASTNodeDescriptor.getNodeNames().get(i))
        .set("chars", i -> CppFileGenerator.toCharArray(ASTNodeDescriptor.getNodeNames().get(i)));
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());

    CppTemplate template = CppTemplate.TREE_CONSTANTS;
    template.render(o, options, JJTreeGlobals.parserName);
  }

  private static String getVisitorArgumentType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void *" : ret;
  }

  private static String getVisitorReturnType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void " : ret;
  }

  private void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    List<String> nodeNames =
        ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void")).collect(Collectors.toList());

    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodeNames).set("type", n -> o.getNodePrefix() + n);

    String argumentType = CppTreeGenerator.getVisitorArgumentType(o);
    String returnType = CppTreeGenerator.getVisitorReturnType(o);
    if (!o.getVisitorDataType().equals("")) {
      argumentType = o.getVisitorDataType();
    }

    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("RETURN_TYPE", returnType);
    options.set("RETURN", returnType.equals("void") ? "" : "return ");
    options.set("IS_MULTI", o.getMulti());

    CppTemplate template = CppTemplate.VISITOR;
    template.render(o, options, JJTreeGlobals.parserName);
  }
}
