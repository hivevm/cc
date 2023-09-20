// Copyright 2011 Google Inc. All Rights Reserved.
// Author: sreeni@google.com (Sreeni Viswanadha)

package it.smartio.fastcc.generator.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.smartio.fastcc.FastCC;
import it.smartio.fastcc.generator.JJTreeCodeGenerator;
import it.smartio.fastcc.jjtree.ASTNodeDescriptor;
import it.smartio.fastcc.jjtree.JJTreeGlobals;
import it.smartio.fastcc.jjtree.JJTreeOptions;
import it.smartio.fastcc.jjtree.NodeScope;
import it.smartio.fastcc.utils.DigestOptions;
import it.smartio.fastcc.utils.DigestWriter;
import it.smartio.fastcc.utils.Template;

public class JavaTreeGenerator extends JJTreeCodeGenerator {

  @Override
  protected final String getTryFinally() {
    return "finally ";
  }

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, PrintWriter io, String indent, JJTreeOptions options) {
    String type = ns.node_descriptor.getNodeType();
    final String nodeClass;
    if ((options.getNodeClass().length() > 0) && !options.getMulti()) {
      nodeClass = options.getNodeClass();
    } else {
      nodeClass = type;
    }

    // Ensure that there is a template definition file for the node type.
    JavaTreeGenerator.ensure(io, type, options);

    io.print(indent + nodeClass + " " + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.node_descriptor.getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      io.println(
          "(" + nodeClass + ")" + options.getNodeFactory() + ".jjtCreate(" + ns.node_descriptor.getNodeId() + ");");
    } else {
      io.println("new " + nodeClass + "(this, " + ns.node_descriptor.getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      io.println(indent + getBoolean() + " " + ns.closedVar + " = true;");
    }
    io.println(indent + ns.node_descriptor.openNode(ns.nodeVar));
    if (options.getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (options.getTrackTokens()) {
      io.println(indent + ns.nodeVar + getPointer() + "jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, PrintWriter io, Enumeration<String> thrown_names,
      String indent) {
    String thrown;
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

      while (thrown_names.hasMoreElements()) {
        thrown = thrown_names.nextElement();
        io.println(indent + "  if (" + ns.exceptionVar + " instanceof " + thrown + ") {");
        io.println(indent + "    throw (" + thrown + ")" + ns.exceptionVar + ";");
        io.println(indent + "  }");
      }
      /*
       * This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
       * otherwise we want to force the user to declare it by crashing on the bad cast.
       */
      io.println(indent + "  throw (Error)" + ns.exceptionVar + ";");
    }
  }

  @Override
  public final void generateJJTree(JJTreeOptions o) {
    JavaTreeGenerator.generateTreeConstants(o);
    JavaTreeGenerator.generateVisitors(o);
    JavaTreeGenerator.generateDefaultVisitors(o);
    JavaTreeGenerator.generateTreeState(o);
  }

  private static void generateTreeState(JJTreeOptions o) {
    DigestOptions options = DigestOptions.get(o);
    options.put(FastCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.put(FastCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    File file = new File(o.getOutputDirectory(), "JJT" + JJTreeGlobals.parserName + "State.java");
    try (DigestWriter ostr = DigestWriter.create(file, FastCC.VERSION, options)) {
      Template.of("/templates/TreeState.template", options).write(ostr);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static Set<String> nodesGenerated = new HashSet<>();

  private static void ensure(PrintWriter io, String nodeType, JJTreeOptions o) {
    File file = new File(o.getOutputDirectory(), nodeType + ".java");

    if (nodeType.equals("Tree")) {} else if (nodeType.equals("Node")) {
      JavaTreeGenerator.ensure(io, "Tree", o);
    } else {
      JavaTreeGenerator.ensure(io, "Node", o);
    }

    if (!(nodeType.equals("Node") || o.getBuildNodeFiles())) {
      return;
    }

    if (file.exists() && JavaTreeGenerator.nodesGenerated.contains(file.getName())) {
      return;
    }

    DigestOptions options = DigestOptions.get(o);
    options.put(FastCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.put(FastCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    try (DigestWriter writer = DigestWriter.create(file, FastCC.VERSION, options)) {
      JavaTreeGenerator.nodesGenerated.add(file.getName());

      if (nodeType.equals("Tree")) {
        Template.of("/templates/Tree.template", options).write(writer);
      } else if (nodeType.equals("Node")) {
        Template.of("/templates/Node.template", options).write(writer);
      } else {
        options.put(FastCC.JJTREE_NODE_TYPE, nodeType);
        options.put(FastCC.JJTREE_VISITOR_RETURN_VOID, Boolean.valueOf(o.getVisitorReturnType().equals("void")));
        Template.of("/templates/MultiNode.template", options).write(writer);
      }
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generatePrologue(PrintWriter ostr, JJTreeOptions o) {
    ostr.println("package " + o.getJavaPackage() + ";");
    ostr.println();
  }

  private static void generateTreeConstants(JJTreeOptions o) {
    String name = JJTreeGlobals.parserName + "TreeConstants";
    File file = new File(o.getOutputDirectory(), name + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, FastCC.VERSION, DigestOptions.get(o))) {
      List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public interface " + name);
      ostr.println("{");

      for (int i = 0; i < nodeIds.size(); ++i) {
        String n = nodeIds.get(i);
        ostr.println("  public final int " + n + " = " + i + ";");
      }

      ostr.println();
      ostr.println();

      ostr.println("  public static String[] jjtNodeName = {");
      for (String n : nodeNames) {
        ostr.println("    \"" + n + "\",");
      }
      ostr.println("  };");

      ostr.println("}");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    String name = JJTreeGlobals.parserName + "Visitor";
    File file = new File(o.getOutputDirectory(), name + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, FastCC.VERSION, DigestOptions.get(o))) {
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public interface " + name);
      ostr.println("{");

      String ve = JavaTreeGenerator.mergeVisitorException(o);

      String argumentType = "Object";
      if (!o.getVisitorDataType().equals("")) {
        argumentType = o.getVisitorDataType();
      }

      ostr.println("  public " + o.getVisitorReturnType() + " visit(Node node, " + argumentType + " data)" + ve + ";");
      if (o.getMulti()) {
        for (String n : nodeNames) {
          if (n.equals("void")) {
            continue;
          }
          String nodeType = o.getNodePrefix() + n;
          ostr.println("  public " + o.getVisitorReturnType() + " visit(" + nodeType + " node, " + argumentType
              + " data)" + ve + ";");
        }
      }
      ostr.println("}");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateDefaultVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    String className = JJTreeGlobals.parserName + "DefaultVisitor";
    File file = new File(o.getOutputDirectory(), className + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, FastCC.VERSION, DigestOptions.get(o))) {
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public class " + className + " implements " + JJTreeGlobals.parserName + "Visitor{");

      final String ve = JavaTreeGenerator.mergeVisitorException(o);

      String argumentType = "Object";
      if (!o.getVisitorDataType().equals("")) {
        argumentType = o.getVisitorDataType().trim();
      }

      final String returnType = o.getVisitorReturnType().trim();
      final boolean isVoidReturnType = "void".equals(returnType);

      ostr.println("  public " + returnType + " defaultVisit(Node node, " + argumentType + " data)" + ve + "{");
      ostr.println("    node.childrenAccept(this, data);");
      ostr.print("    return");
      if (!isVoidReturnType) {
        if (returnType.equals(argumentType)) {
          ostr.print(" data");
        } else if ("boolean".equals(returnType)) {
          ostr.print(" false");
        } else if ("int".equals(returnType)) {
          ostr.print(" 0");
        } else if ("long".equals(returnType)) {
          ostr.print(" 0L");
        } else if ("double".equals(returnType)) {
          ostr.print(" 0.0d");
        } else if ("float".equals(returnType)) {
          ostr.print(" 0.0f");
        } else if ("short".equals(returnType)) {
          ostr.print(" 0");
        } else if ("byte".equals(returnType)) {
          ostr.print(" 0");
        } else if ("char".equals(returnType)) {
          ostr.print(" '\u0000'");
        } else {
          ostr.print(" null");
        }
      }
      ostr.println(";");
      ostr.println("  }");

      ostr.println("  public " + returnType + " visit(Node node, " + argumentType + " data)" + ve + "{");
      ostr.println("    " + (isVoidReturnType ? "" : "return ") + "defaultVisit(node, data);");
      ostr.println("  }");

      if (o.getMulti()) {
        for (String n : nodeNames) {
          if (n.equals("void")) {
            continue;
          }
          String nodeType = o.getNodePrefix() + n;
          ostr.println(
              "  public " + returnType + " visit(" + nodeType + " node, " + argumentType + " data)" + ve + "{");
          ostr.println("    " + (isVoidReturnType ? "" : "return ") + "defaultVisit(node, data);");
          ostr.println("  }");
        }
      }

      ostr.println("}");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static String mergeVisitorException(JJTreeOptions o) {
    String ve = o.getVisitorException();
    if (!"".equals(ve)) {
      ve = " throws " + ve;
    }
    return ve;
  }
}
