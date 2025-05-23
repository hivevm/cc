// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.hivevm.cc.Language;
import org.hivevm.cc.ParserBuilder;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    config.getTasks().forEach(s -> process(s, config));
  }

  /**
   * Get the {@link File} from pathname.
   *
   * @param pathname
   */
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

  /**
   * Processes the {@link ParserTask}
   *
   * @param task
   * @param target
   */
  protected void process(ParserTask task, ParserProject config) {
    Language target = (config.target == null) ? Language.JAVA : config.target;
    Language language = (task.target == null) ? target : task.target;

    ParserBuilder builder = ParserBuilder.of(language);
    builder.setTargetDir(getFile(task.output == null ? config.output : task.output));
    builder.setParserFile(getFile(task.jjFile == null ? config.jjFile : task.jjFile));
    builder.setTreeFile(getFile(task.jjtFile == null ? config.jjtFile : task.jjtFile));
    builder.setCustomNodes(task.treeNodes);
    builder.build();
  }
}