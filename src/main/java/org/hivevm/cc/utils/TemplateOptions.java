// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hivevm.cc.parser.Options;
import org.hivevm.core.Environment;
import org.hivevm.source.SourceWriter;
import org.jspecify.annotations.NonNull;


/**
 * The {@code TemplateOptions} class provides a flexible mechanism to manage key-value options
 * within an environment. It allows the definition and retrieval of options, and supports setting
 * options with direct values, suppliers, writers, or custom mappers.
 */
public class TemplateOptions implements Options {

    private final Environment         environment;
    private final Map<String, Object> options = new HashMap<>();

    /**
     * Constructs a new instance of the {@code TemplateOptions} class with the specified environment.
     */
    public TemplateOptions(Environment environment) {
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
            StringWriter builder = new StringWriter();
            try (var writer = new SourcePrinter(builder)) {
                value.accept(writer);
            }
            return builder.toString();
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
            TemplateOptions.this.options.put(String.join(".", this.name, key), function);
            return this;
        }

        public final Mapper<T> set(String key, BiConsumer<T, SourceWriter> consumer) {
            set(key, i -> {
                StringWriter builder = new StringWriter();
                try (var writer = new SourcePrinter(builder)) {
                    consumer.accept(i, writer);
                }
                return builder.toString();
            });
            return this;
        }
    }

    public static SourceWriter createWriter(StringWriter writer) {
        return new SourcePrinter(writer);
    }

    private static class SourcePrinter extends PrintWriter implements SourceWriter {
        public SourcePrinter(StringWriter writer) {
            super(writer);
        }

        @Override
        public SourceWriter append(@NonNull String text) {
            write(text);
            return this;
        }
    }
}
