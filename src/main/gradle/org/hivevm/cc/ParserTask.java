// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.gradle.api.Project;

import java.util.List;

import javax.inject.Inject;

public class ParserTask {

  private final Project project;


  public String       name;
  public Language     target;

  public String       jjFile;
  public String       jjtFile;

  public String       output;
  public List<String> excludes;


  @Inject
  public ParserTask(Project project) {
    this.project = project;
  }

  public final Project getProject() {
    return this.project;
  }
}
