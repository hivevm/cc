// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * The {@link ParserGenerator} class.
 */
public abstract class ParserGenerator extends DefaultTask {

  @Inject
  public ParserGenerator() {
    setGroup("HiveVM");
    setDescription("Generates a parser");
  }

  @Input
  @Optional
  @Option(option = "target", description = "Sets the target language.")
  public abstract Property<Language> getTarget();

  @OptionValues("target")
  public List<Language> getAvailableOutputTypes() {
    return new ArrayList<>(Arrays.asList(Language.values()));
  }

  @TaskAction
  public void process() {
    ParserProject config = getProject().getExtensions().findByType(ParserProject.class);

    if (config == null) {
      getProject().getLogger().error("No configuration defined");
      return;
    }

    Language defaultTarget = (config.target == null) ? Language.JAVA : config.target;
    config.getTasks().forEach(s -> process(s, defaultTarget));
  }

  protected File getFile(String pathname) {
    if (pathname == null) {
      return null;
    }

    File targetDir = new File(pathname);
    if (targetDir.isAbsolute()) {
      return targetDir;
    }

    File projectDir = getProject().getProjectDir();
    return new File(projectDir, pathname);
  }

  protected void process(ParserTask task, Language target) {
    Language language = (task.target == null) ? target : task.target;
    HiveCCBuilder builder = HiveCCBuilder.of(language);
    builder.setTargetDir(getFile(task.output));
    builder.setJJTreeFile(getFile(task.jjtFile));
    builder.setJJFile(getFile(task.jjFile));
    builder.setExcludes(task.excludes);
    builder.build();
  }
}