// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hivevm.cc.parser.Options;
import org.jspecify.annotations.NonNull;


/**
 * The {@code TemplateOptions} class provides a flexible mechanism to manage key-value options
 * within an environment. It allows the definition and retrieval of options, and supports setting
 * options with direct values, suppliers, writers, or custom mappers.
 */
public class TemplateLambda implements Options {

    private final Environment         environment;
    private final Map<String, Object> options = new HashMap<>();

    /**
     * Constructs a new instance of the {@code TemplateOptions} class with the specified environment.
     */
    public TemplateLambda(Environment environment) {
        this.environment = environment;
    }

    /**
     * Checks whether the specified name exists either in the current options map
     * or in the underlying environment.
     */
    @Override
    public final boolean has(String name) {
        return this.options.containsKey(name) || this.environment.has(name);
    }

    /**
     * Retrieves the value associated with the specified name.
     * If the name exists in the current options map, its corresponding value is returned.
     * Otherwise, the value is retrieved from the underlying environment.
     */
    @Override
    public final Object get(String name) {
        return this.options.containsKey(name) ? this.options.get(name) : this.environment.get(name);
    }

    /**
     * Associates the specified value with the given name in the current options map.
     * If the name already exists in the map, its value is updated to the given value.
     */
    public final void set(String name, Object value) {
        this.options.put(name, value);
    }

    public final void set(String name, Supplier<Object> value) {
        this.options.put(name, value);
    }

    public final void setWriter(String name, Consumer<SourceWriter> value) {
        set(name, () -> {
            var writer = new SourcePrinter();
            value.accept(writer);
            return writer.toString();
        });
    }

    public final void set(String name, Function<?, Object> value) {
        this.options.put(name, value);
    }

    public final <T> Mapper<T> add(String name, T value) {
        this.options.put(name, value);
        return new Mapper<>(name);
    }

    public final <T> Mapper<T> add(String name, Iterable<T> value) {
        this.options.put(name, value);
        return new Mapper<>(name);
    }

    public class Mapper<T> {

        private final String name;

        /**
         * Constructs an instance of {@link Mapper}.
         */
        private Mapper(String name) {
            this.name = name;
        }

        public final Mapper<T> set(String key, Function<T, Object> function) {
            TemplateLambda.this.options.put(String.join(".", this.name, key), function);
            return this;
        }

        public final Mapper<T> set(String key, BiConsumer<T, SourceWriter> consumer) {
            set(key, i -> {
                var writer = new SourcePrinter();
                consumer.accept(i, writer);
                return writer.toString();
            });
            return this;
        }
    }

    private static class SourcePrinter implements SourceWriter {

        private final StringWriter builder;

        public SourcePrinter() {
            this.builder = new StringWriter();
        }

        @Override
        public SourceWriter append(@NonNull String s) {
            builder.append(s);
            return this;
        }
        public void println(@NonNull String s) {
            builder.append(s).append("\n");
        }

        public String toString() {
            return builder.toString();
        }
    }
}
