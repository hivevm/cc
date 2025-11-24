// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hivevm.core.Environment;

/**
 * Represents a template rendering system that interprets a set of commands embedded
 * within input data, allowing conditional logic and repetitive constructs.
 * A template is processed with an underlying environment, and the output is written
 * to a specified {@link PrintWriter}.
 *
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing
 * commands are parsed and constructed into a tree structure, which is then rendered
 * dynamically based on the provided environment.
 */
public class Template {

    private static final String COND_IF      = "if";
    private static final String COND_ELSE_IF = "elif";
    private static final String COND_ELSE    = "else";
    private static final String COND_FOREACH = "foreach";

    private static final String COND_END    = "end";
    private static final String COND_END_IF = "fi";

    // COMMAND (ARGUMENT (,ARGUMENT)* )?
    private static final Pattern COMMAND = Pattern.compile(
            "@(if|elif|else|foreach|fi|end)(?:\\s*\\(([^)]+)\\))?\\n?|\\{\\{([^{}:]+)(?::([^}]*))?}}");

    private final byte[]      bytes;
    private final Environment environment;

    /**
     *
     */
    public Template(byte[] bytes, Environment environment) {
        this.bytes = bytes;
        this.environment = environment;
    }

    /**
     * Use the template.
     */
    public final void render(PrintWriter writer) {
        var data = new String(this.bytes, StandardCharsets.UTF_8);
        var matcher = Template.COMMAND.matcher(data);
        var root = new TemplateTree();
        var offset = walk(root, data, 0, matcher);
        if (offset < data.length()) {
            root.newText(data.substring(offset));
        }
        root.render(writer, this.environment);
    }

    /**
     * Use the template.
     */
    private int walk(TemplateTree node, String data, int offset, Matcher matcher) {
        while (matcher.find()) {
            if (matcher.start() > offset) {
                node.newText(data.substring(offset, matcher.start()));
            }
            offset = matcher.end();

            var cond = matcher.group(1);
            if (cond != null) {
                switch (cond) {
                    case COND_IF:
                        var child = node.newSwitch();
                        while (!Template.COND_END_IF.equals(matcher.group(1))) {
                            switch (matcher.group(1)) {
                                case COND_IF:
                                case COND_ELSE:
                                case COND_ELSE_IF:
                                    TemplateTree n = child.newCase(matcher.group(2));
                                    offset = walk(n, data, offset, matcher);
                            }
                        }
                        break;

                    case COND_ELSE:
                    case COND_ELSE_IF:
                    case COND_END:
                    case COND_END_IF:
                        return offset;

                    case COND_FOREACH:
                        var args = Arrays.stream(matcher.group(2)
                                .split(":"))
                                .map(String::trim)
                                .toList();
                        child = node.newForEach(args.get(0), args.get(1));
                        offset = walk(child, data, offset, matcher);
                        break;

                    default:
                        break;
                }
            }
            else {
                node.newExpr(matcher.group(3), matcher.group(4));
            }
        }
        return offset;
    }
}
