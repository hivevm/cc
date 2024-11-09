// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

/**
 * The {@link GradlePlugin} defines the different tasks required for a smart.IO build management.
 */
public class GradlePlugin implements Plugin<Project> {

  public static final String CONFIG = "hiveCC";


  @Override
  public void apply(Project project) {
    ExtensionContainer extension = project.getExtensions();
    extension.create(GradlePlugin.CONFIG, GeneratorConfig.class);

    project.getTasks().register("generateParser", GeneratorTask.class);
  }
}