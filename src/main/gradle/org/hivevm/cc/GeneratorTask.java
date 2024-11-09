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
import org.hivevm.cc.HiveCCBuilder;
import org.hivevm.cc.HiveCCBuilder.Language;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * The {@link GeneratorTask} class.
 */
public abstract class GeneratorTask extends DefaultTask {

  @Inject
  public GeneratorTask() {
    setGroup("HiveVM");
    setDescription("Generates a parser");
  }

  @Input
  @Optional
  @Option(option = "target", description = "Sets the target language.")
  public abstract Property<Target> getTarget();

  @OptionValues("target")
  public List<Target> getAvailableOutputTypes() {
    return new ArrayList<Target>(Arrays.asList(Target.values()));
  }

  @TaskAction
  public void process() {
    GeneratorConfig config = getProject().getExtensions().findByType(GeneratorConfig.class);

    if (config == null) {
      getProject().getLogger().error("No configuration defined");
      return;
    }

    Target defaultTarget = (config.target == null) ? Target.JAVA : config.target;

    config.getSteps().forEach(s -> process(s, defaultTarget));
  }

  protected File getFile(String pathname) {
    if (pathname == null)
      return null;

    File targetDir = new File(pathname);
    if (targetDir.isAbsolute())
      return targetDir;

    File projectDir = getProject().getProjectDir();
    return new File(projectDir, pathname);
  }


  protected void process(GeneratorStep step, Target target) {
    Target lang = (step.target == null) ? target : step.target;
    HiveCCBuilder builder = HiveCCBuilder.of(lang == Target.CPP ? Language.Cpp : Language.Java);
    builder.setTargetDir(getFile(step.directory));
    builder.setJJTreeFile(getFile(step.jjtFile));
    builder.setJJFile(getFile(step.jjFile));
    builder.setExcludes(step.excludes);
    builder.build();
  }
}