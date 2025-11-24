// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Stack;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link PrintWriter}.
 * <p>
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing commands are
 * parsed and constructed into a tree structure, which is then rendered dynamically based on the
 * provided environment.
 */
public class Template {

    private enum Function {
        IF,
        ELIF,
        ELSE,
        FOREACH,
        FI,
        END,
        VAR
    }

    private static final String FUNCTIONS = Stream.of(Function.values())
        .map(l -> l.name().toLowerCase())
        .collect(Collectors.joining("|"));

    // PATTERN = VERTICAL? HORIZONTAL* @FUNCTION (PARAMETER)? HORIZONTAL* VERTICAL?
    private static final Pattern PATTERN = Pattern.compile("(\\v?)(\\h*)(?:@(" +
        FUNCTIONS + ")(?:\\(([^)]+)\\))?|\\{\\{([^}]+)}})(\\h*)(\\v?)", Pattern.MULTILINE);

    private final String text;

    /**
     * Constructs a new instance of the Template class using the provided byte array.
     */
    public Template(byte[] bytes) {
        this(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Constructs a new instance of the Template class using the provided byte array.
     */
    public Template(String text) {
        this.text = text.replace("\r", "");
    }

    /**
     * Renders the template content using the provided environment and writes the result to the
     * specified output stream. The method processes template commands and uses a hierarchical
     * structure to construct and render the output.
     */
    public final void render(String title, OutputStream outputStream, Environment environment) {
        var renderer = TemplateRenderer.create();
        var nodes = new Stack<TemplateRenderer>();
        nodes.push(renderer);

        var offset = 0;
        var matcher = Template.PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > offset) {
                nodes.peek().addText(text.substring(offset, matcher.start()));
            }
            offset = matcher.end();

            var space_begin = matcher.group(2);
            var space_end = matcher.group(6);

            var is_inline = matcher.group(1).isEmpty() || matcher.group(7).isEmpty();
            if (!is_inline) {
                nodes.peek().addText("\n");
            }

            var isFunc = matcher.group(5) == null;
            var func = isFunc ? matcher.group(3).toUpperCase() : "VAR";
            var param = isFunc ? matcher.group(4) : matcher.group(5);
            switch (Function.valueOf(func)) {
                case IF:
                    var node = nodes.peek().addMatch();
                    nodes.push(node);
                    node = node.addCase(param);
                    nodes.push(node);
                    break;

                case ELIF:
                case ELSE:
                    nodes.pop();
                    node = nodes.peek().addCase(param);
                    nodes.push(node);
                    break;

                case FI:
                    nodes.pop();
                case END:
                    nodes.pop();
                    break;

                case FOREACH:
                    var args = Arrays.stream(param.split(":"))
                        .map(String::trim)
                        .toList();
                    node = nodes.peek().addForeach(args.get(0), args.get(1));
                    nodes.push(node);
                    break;

                case VAR:
                    nodes.peek().addText(space_begin);
                    nodes.peek().addVar(param);
                    nodes.peek().addText(space_end);
                    if (!matcher.group(7).isEmpty()) {
                        nodes.peek().addText("\n");
                    }

                default:
                    break;
            }
        }
        if (offset < text.length()) {
            renderer.addText(text.substring(offset));
        }

        try (var writer = TemplateWriter.create(title, outputStream, environment)) {
            renderer.render(writer, writer);
        }
    }
}
