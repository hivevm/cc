// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
package org.hivevm.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

interface TemplateRenderer {

    /**
     * Renders content to the provided {@link SourceWriter} using the specified {@link Environment}.
     * The method processes the environment's contextual data to dynamically generate the output.
     */
    void render(SourceWriter writer, Environment environment);

    /**
     * Adds a block of text to the renderer. The provided text will be handled as raw content and
     * included in the rendered output as-is, without any additional processing or interpretation.
     */
    default void addText(String text) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a variable to the renderer's context. The variable is specified as an expression, which
     * will be dynamically evaluated in the rendering environment. This allows the rendered output
     * to include values derived from the environment's state.
     */
    default void addVar(String expression) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a new match case renderer to the current renderer. The match renderer allows conditional
     * rendering based on an environment's variables or flags. The matched case is dynamically
     * selected at render time depending on whether conditions associated with environment states
     * are satisfied.
     */
    default TemplateRenderer addMatch() {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a conditional case to the renderer. This method associates a specific case with its
     * conditional expression. The case will be evaluated and rendered dynamically based on whether
     * the condition defined by the expression evaluates to true during runtime.
     */
    default TemplateRenderer addCase(String expression) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a "foreach" directive to the renderer. This directive processes a collection by
     * iterating over its elements, associating each element with a specified variable that can be
     * referenced during rendering. The variable will be substituted with each element of the list
     * sequentially as the iteration proceeds.
     */
    default TemplateRenderer addForeach(String var, String list) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates and returns a new instance of a {@link TemplateRenderer}. The returned instance is a
     * {@link ListRenderer}, which supports managing and rendering a list of {@link TemplateRenderer} nodes
     * in a sequential order.
     */
    static TemplateRenderer create() {
        return new ListRenderer();
    }

    /**
     * A record that implements the {@link TemplateRenderer} interface for rendering plain text. This class
     * is responsible for directly outputting the provided text to the specified
     * {@link SourceWriter}, without any additional processing or evaluation of the environment.
     */
    record TextRenderer(String text) implements TemplateRenderer {

        @Override
        public void render(SourceWriter writer, Environment environment) {
            writer.append(text);
        }
    }

    /**
     * A record implementing the {@link TemplateRenderer} interface that renders the value of a variable
     * defined in the provided {@link Environment}. The variable name is specified as a string
     * during the record's instantiation.
     */
    record VarRenderer(String text) implements TemplateRenderer {

        @Override
        public void render(SourceWriter writer, Environment environment) {
            if (environment.has(text)) {
                Object value = environment.get(text);
                if (value instanceof Consumer)
                    ((Consumer<SourceWriter>) value).accept(writer);
                else if (value instanceof Supplier)
                    writer.append(((Supplier<String>) value).get());
                else if (value != null)
                    writer.append(value.toString());
            }
        }
    }

    /**
     * A record implementing the {@link TemplateRenderer} interface, responsible for managing and rendering
     * a list of {@link TemplateRenderer} nodes. Each node in the list is rendered in sequence, allowing
     * complex render hierarchies to be constructed.
     */
    record ListRenderer(List<TemplateRenderer> nodes) implements TemplateRenderer {

        public ListRenderer() {
            this(new ArrayList<>());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            nodes.forEach(n -> n.render(writer, environment));
        }

        @Override
        public void addText(String text) {
            nodes.add(new TextRenderer(text));
        }

        @Override
        public void addVar(String expression) {
            nodes.add(new VarRenderer(expression));
        }

        @Override
        public MatchRenderer addMatch() {
            var node = new MatchRenderer();
            nodes.add(node);
            return node;
        }

        @Override
        public ForEachRenderer addForeach(String var, String list) {
            var node = new ForEachRenderer(var, list);
            nodes.add(node);
            return node;
        }
    }

    /**
     * A record that implements the {@link TemplateRenderer} interface to enable conditional rendering based
     * on environment variables. The {@code MatchRenderer} evaluates the conditions associated with
     * the provided map of {@link TemplateRenderer} nodes and renders the first node whose condition is
     * satisfied. If no condition is satisfied, a default renderer is used, if provided.
     */
    record MatchRenderer(Map<String, TemplateRenderer> nodes) implements TemplateRenderer {

        private static final String DEFAULT = "_";

        public MatchRenderer() {
            this(new HashMap<>());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            var result = nodes.keySet().stream()
                .filter(environment::has)
                .filter(n -> validate(n, environment))
                .findFirst();
            if (result.isPresent()) {
                nodes.get(result.get()).render(writer, environment);
            }
            else if (nodes.containsKey(DEFAULT)) {
                nodes.get(DEFAULT).render(writer, environment);
            }
        }

        @Override
        public ListRenderer addCase(String expression) {
            var node = new ListRenderer();
            nodes.put(expression != null ? expression : DEFAULT, node);
            return node;
        }
    }

    /**
     * A record that implements the {@link TemplateRenderer} interface to enable conditional rendering based
     * on environment variables. The {@code MatchRenderer} evaluates the conditions associated with
     * the provided map of {@link TemplateRenderer} nodes and renders the first node whose condition is
     * satisfied. If no condition is satisfied, a default renderer is used, if provided.
     */
    record ForEachRenderer(String var, String list, ListRenderer renderer) implements
        TemplateRenderer {

        public ForEachRenderer(String var, String list) {
            this(var, list, new ListRenderer());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            var result = environment.get(list);
            if (result instanceof Integer integer) {
                for (var i = 0; i < integer; i++) {
                    renderer.render(writer, new ListEnv(environment, var, list, i));
                }
            }
            else if (result instanceof Iterable<?> iterable) {
                for (var elem : iterable) {
                    renderer.render(writer, new ListEnv(environment, var, list, elem));
                }
            }
        }

        @Override
        public void addText(String text) {
            renderer.addText(text);
        }

        @Override
        public void addVar(String expression) {
            renderer.addVar(expression);
        }

        @Override
        public TemplateRenderer addMatch() {
            return renderer.addMatch();
        }

        @Override
        public TemplateRenderer addForeach(String var, String list) {
            return renderer.addForeach(var, list);
        }
    }

    private static boolean validate(String expression, Environment environment) {
        if (expression.startsWith("!")) { // negative condition
            return !validate(expression.substring(1), environment);
        }

        if (!environment.has(expression))
            return false;

        return switch (environment.get(expression)) {
            case String text when !text.isEmpty() -> true;
            case Number number when number.intValue() != 0 -> true;
            case Boolean bool when bool -> true;
            case null -> false;
            default -> false;
        };
    }

    /**
     * Represents an environment that overlays another environment, allowing the addition of local
     * variables that override or supplement the variables in the underlying environment.
     * <p>
     * This class allows setting and retrieving variables while respecting the underlying
     * environment if a variable is not explicitly set in the current instance.
     */
    class Env implements Environment {

        private final Environment         environment;
        private final Map<String, Object> options;

        /**
         * Constructs a TemplateEnv instance with the specified underlying environment.
         */
        private Env(Environment environment) {
            this.environment = environment;
            this.options = new HashMap<>();
        }

        /**
         * Checks if the specified name exists in either the local environment variables or the
         * underlying environment.
         * <p>
         * The method first checks if the name exists in the local `options` map of the current
         * instance. If the name does not exist locally, it then delegates to the `has` method of
         * the underlying environment.
         */
        @Override
        public final boolean has(String name) {
            return this.options.containsKey(name) || this.environment.has(name);
        }

        /**
         * Retrieves the value associated with the specified name from the current environment.
         * <p>
         * This method first checks if the name exists in the local `options` map. If it exists, the
         * corresponding value is returned. If the name does not exist in the local map, the method
         * delegates the retrieval to the underlying environment using its `get` method.
         */
        @Override
        public final Object get(String name) {
            return this.options.containsKey(name) ? this.options.get(name)
                : this.environment.get(name);
        }

        /**
         * Sets the specified name-value pair in the local environment.
         * <p>
         * This method associates the given name with the specified value in the local `options` map
         * of this environment instance, overriding any existing value associated with the name in
         * this map. It does not affect the underlying environment.
         */
        public Env set(String name, Object value) {
            this.options.put(name, value);
            return this;
        }
    }

    /**
     * Represents an environment that overlays another environment, allowing the addition of local
     * variables that override or supplement the variables in the underlying environment.
     * <p>
     * This class allows setting and retrieving variables while respecting the underlying
     * environment if a variable is not explicitly set in the current instance.
     */
    class ListEnv implements Environment {

        private final Environment environment;
        private final String      var;
        private final String      list;
        private final Object      value;

        /**
         * Constructs a TemplateEnv instance with the specified underlying environment.
         */
        private ListEnv(Environment environment, String var, String list, Object value) {
            this.environment = environment;
            this.var = var;
            this.list = list;
            this.value = value;
        }

        /**
         * Checks if the specified name exists in either the local environment variables or the
         * underlying environment.
         * <p>
         * The method first checks if the name exists in the local `options` map of the current
         * instance. If the name does not exist locally, it then delegates to the `has` method of
         * the underlying environment.
         */
        @Override
        public final boolean has(String name) {
            return name.equals(var)
                || name.startsWith(var + ".")
                && environment.has(list + "." + name.substring(var.length() + 1))
                || environment.has(name);
        }

        /**
         * Retrieves the value associated with the specified name from the current environment.
         * <p>
         * This method first checks if the name exists in the local `options` map. If it exists, the
         * corresponding value is returned. If the name does not exist in the local map, the method
         * delegates the retrieval to the underlying environment using its `get` method.
         */
        @Override
        public final Object get(String name) {
            if (name.equals(var))
                return value;
            if (name.startsWith(var + ".") && environment.has(
                list + "." + name.substring(var.length() + 1))) {
                Object func = environment.get(list + "." + name.substring(var.length() + 1));
                return ((Function<Object, Object>) func).apply(value);
            }
            return environment.get(name);
        }
    }
}
