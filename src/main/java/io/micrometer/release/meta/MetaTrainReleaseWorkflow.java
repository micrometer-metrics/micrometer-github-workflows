/*
 * Copyright 2025 Broadcom.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.release.meta;

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class MetaTrainReleaseWorkflow {

    private static final Logger log = LoggerFactory.getLogger(MetaTrainReleaseWorkflow.class);

    private final PostReleaseWorkflow postReleaseWorkflow;

    public MetaTrainReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow) {
        this.postReleaseWorkflow = postReleaseWorkflow;
    }

    public void run(List<ProjectSetup> projectSetups) {
        log.info("Starting meta release...");
        projectSetups.forEach(projectSetup -> {
            log.info("Running single release train for project [{}] and {} versions...", projectSetup.ghOrgRepo(),
                    projectSetup.versionsForThisProject());
            newProjectTrainReleaseWorkflow(projectSetup).run(projectSetup);
            log.info("Single release train for project [{}] and {} versions completed!", projectSetup.ghOrgRepo(),
                    projectSetup.versionsForThisProject());
        });
        log.info("Meta release completed!");
    }

    private ProjectTrainReleaseWorkflow newProjectTrainReleaseWorkflow(ProjectSetup projectSetup) {
        File projectSubfolder = new File(projectSetup.ghRepo());
        log.info("Creating a subfolder [{}] for project [{}]", projectSubfolder, projectSetup.ghOrgRepo());
        projectSubfolder.mkdirs();
        return workflow(projectSetup, projectSubfolder);
    }

    ProjectTrainReleaseWorkflow workflow(ProjectSetup projectSetup, File projectSubfolder) {
        return new ProjectTrainReleaseWorkflow(new ProcessRunner(projectSetup.ghOrgRepo(), projectSubfolder),
                postReleaseWorkflow);
    }

}
