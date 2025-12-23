// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.util.Stack;
import org.hivevm.source.Renderer.ForEachRenderer;
import org.hivevm.source.Renderer.ListRenderer;
import org.hivevm.source.Renderer.TextRenderer;
import org.hivevm.source.Renderer.VarRenderer;

class RendererBuilder {

    private final Stack<Renderer> stack;

    public RendererBuilder() {
        this.stack = new Stack<Renderer>();
        this.stack.push(new ListRenderer());
    }

    /**
     * Adds a block of text to the renderer. The provided text will be handled as raw content and
     * included in the rendered output as-is, without any additional processing or interpretation.
     */
    public final RendererBuilder addText(String text) {
        var node = stack.peek();
        if (node instanceof ListRenderer list) {
            list.nodes().add(new TextRenderer(text));
        }
        else if (node instanceof ForEachRenderer forech) {
            forech.renderer().nodes().add(new TextRenderer(text));
        }
        return this;
    }

    /**
     * Adds a variable to the renderer's context. The variable is specified as an expression, which
     * will be dynamically evaluated in the rendering environment. This allows the rendered output
     * to include values derived from the environment's state.
     */
    public final RendererBuilder addVar(String expression) {
        var node = stack.peek();
        if (node instanceof ListRenderer list) {
            list.nodes().add(new VarRenderer(expression));
        }
        else if (node instanceof ForEachRenderer forech) {
            forech.renderer().nodes().add(new VarRenderer(expression));
        }
        return this;
    }

    /**
     * Adds a new match case renderer to the current renderer. The match renderer allows conditional
     * rendering based on an environment's variables or flags. The matched case is dynamically
     * selected at render time depending on whether conditions associated with environment states
     * are satisfied.
     */
    public final RendererBuilder addMatch(String expression) {
        var node = stack.peek().addMatch();
        stack.push(node);
        stack.push(node.addCase(expression));
        return this;
    }

    /**
     * Adds a conditional case to the renderer. This method associates a specific case with its
     * conditional expression. The case will be evaluated and rendered dynamically based on whether
     * the condition defined by the expression evaluates to true during runtime.
     */
    public final RendererBuilder addCase(String expression) {
        stack.pop();
        stack.push(stack.peek().addCase(expression));
        return this;
    }

    /**
     * Adds a "foreach" directive to the renderer. This directive processes a collection by
     * iterating over its elements, associating each element with a specified variable that can be
     * referenced during rendering. The variable will be substituted with each element of the list
     * sequentially as the iteration proceeds.
     */
    public final RendererBuilder addForeach(String param) {
        var newNode = new ForEachRenderer(param);
        var node = stack.peek();
        if (node instanceof ListRenderer list) {
            list.nodes().add(newNode);
        }
        else if (node instanceof ForEachRenderer forech) {
            forech.renderer().nodes().add(newNode);
        }
        stack.push(newNode);
        return this;
    }

    public final RendererBuilder pop() {
        stack.pop();
        return this;
    }

    public final Renderer build() {
        return stack.pop();
    }
}
