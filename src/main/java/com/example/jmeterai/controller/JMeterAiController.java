package com.example.jmeterai.controller;

import com.example.jmeterai.model.ProjectResult;
import com.example.jmeterai.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/project")
public class JMeterAiController {

    @Autowired
    private PipelineService pipelineService;

    // In-memory task storage
    private final Map<String, TaskInfo> tasks = new ConcurrentHashMap<>();

    @PostMapping("/run")
    public RunResponse runProject(@RequestBody RunRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo();
        task.taskId = taskId;
        task.status = "RUNNING";
        task.startTime = System.currentTimeMillis();
        tasks.put(taskId, task);

        // Run async
        CompletableFuture.runAsync(() -> {
            try {
                ProjectResult result = pipelineService.runPipeline(request.swaggerUrl, request.programName, request.extra);
                task.result = result;
                task.status = "COMPLETED";
            } catch (Exception e) {
                task.status = "FAILED";
                task.error = e.getMessage();
                e.printStackTrace();
            } finally {
                task.endTime = System.currentTimeMillis();
            }
        });

        return new RunResponse(taskId, "Task started successfully");
    }

    @GetMapping("/task/{taskId}")
    public TaskInfo getTaskStatus(@PathVariable String taskId) {
        return tasks.getOrDefault(taskId, new TaskInfo(taskId, "NOT_FOUND"));
    }

    public static class RunRequest {
        public String swaggerUrl;
        public String programName;
        public String extra;
    }

    public static class RunResponse {
        public String taskId;
        public String message;

        public RunResponse(String taskId, String message) {
            this.taskId = taskId;
            this.message = message;
        }
    }

    public static class TaskInfo {
        public String taskId;
        public String status; // RUNNING, COMPLETED, FAILED, NOT_FOUND
        public String error;
        public ProjectResult result;
        public long startTime;
        public long endTime;

        public TaskInfo() {}
        
        public TaskInfo(String taskId, String status) {
            this.taskId = taskId;
            this.status = status;
        }
    }
}
