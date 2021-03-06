/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.standalonecanaryanalysis.orca.stage;

import com.netflix.kayenta.standalonecanaryanalysis.orca.task.MonitorCanaryTask;
import com.netflix.kayenta.standalonecanaryanalysis.orca.task.RunCanaryTask;
import com.netflix.spinnaker.orca.api.pipeline.CancellableStage;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunCanaryStage implements StageDefinitionBuilder, CancellableStage {

  private final ExecutionRepository executionRepository;

  @Autowired
  public RunCanaryStage(ExecutionRepository executionRepository) {
    this.executionRepository = executionRepository;
  }

  public static final String STAGE_TYPE = "runCanary";
  public static final String STAGE_NAME_PREFIX = "Run Canary #";

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    builder
        .withTask("runCanary", RunCanaryTask.class)
        .withTask("monitorCanary", MonitorCanaryTask.class);
  }

  @Nonnull
  @Override
  public String getType() {
    return STAGE_TYPE;
  }

  @Override
  public Result cancel(StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String canaryPipelineExecutionId =
        (String) context.getOrDefault("canaryPipelineExecutionId", null);

    if (canaryPipelineExecutionId != null) {
      log.info(
          "Cancelling StageExecution (stageId: {}: executionId: {}, canaryPipelineExecutionId: {}, context: {})",
          stage.getId(),
          stage.getExecution().getId(),
          canaryPipelineExecutionId,
          stage.getContext());

      try {
        log.info("Cancelling pipeline execution {}...", canaryPipelineExecutionId);

        PipelineExecution pipeline =
            executionRepository.retrieve(ExecutionType.PIPELINE, canaryPipelineExecutionId);

        if (pipeline.getStatus().isComplete()) {
          log.debug(
              "Not changing status of pipeline execution {} to CANCELED since execution is already completed: {}",
              canaryPipelineExecutionId,
              pipeline.getStatus());
          return new CancellableStage.Result(stage, new HashMap<>());
        }

        executionRepository.cancel(ExecutionType.PIPELINE, canaryPipelineExecutionId);
        executionRepository.updateStatus(
            ExecutionType.PIPELINE, canaryPipelineExecutionId, ExecutionStatus.CANCELED);
      } catch (Exception e) {
        log.error(
            "Failed to cancel StageExecution (stageId: {}, executionId: {}), e: {}",
            stage.getId(),
            stage.getExecution().getId(),
            e.getMessage(),
            e);
      }
    } else {
      log.info(
          "Not cancelling StageExecution (stageId: {}: executionId: {}, context: {}) since no canary pipeline execution id exists",
          stage.getId(),
          stage.getExecution().getId(),
          stage.getContext());
    }

    return new CancellableStage.Result(stage, new HashMap<>());
  }
}
