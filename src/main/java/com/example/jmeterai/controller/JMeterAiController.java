package com.example.jmeterai.controller;

import com.example.jmeterai.model.ProjectResult;
import com.example.jmeterai.model.ExecutionResult;
import com.example.jmeterai.model.TestCase;
import com.example.jmeterai.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
                ProjectResult result = pipelineService.runPipeline(request.swaggerUrl, request.programName, request.extra, request.tags, request.authorization);
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

    @PostMapping("/rerun/{taskId}")
    public RunResponse rerunProject(@PathVariable String taskId) {
        TaskInfo originalTask = tasks.get(taskId);
        if (originalTask == null || !"COMPLETED".equals(originalTask.status)) {
            throw new RuntimeException("Task not found or not completed");
        }
        
        String newTaskId = UUID.randomUUID().toString();
        TaskInfo newTask = new TaskInfo();
        newTask.taskId = newTaskId;
        newTask.status = "RUNNING";
        newTask.startTime = System.currentTimeMillis();
        tasks.put(newTaskId, newTask);

        CompletableFuture.runAsync(() -> {
            try {
                String baseUrl = originalTask.result.baseUrl;
                // Fallback logic if baseUrl is missing (legacy data support)
                if (baseUrl == null || baseUrl.isEmpty()) {
                     baseUrl = "http://localhost:8080";
                     if (originalTask.result.executionResults != null && !originalTask.result.executionResults.isEmpty()) {
                         String url = originalTask.result.executionResults.get(0).url;
                         try {
                            // Try to infer base URL (host + optional path prefix)
                            java.net.URL u = new java.net.URL(url);
                            // NOTE: This inference is imperfect as we don't know where the "base" ends. 
                            // But for now, let's stick to protocol+authority as a safe default fallback, 
                            // accepting that it might miss a path prefix like /api.
                            baseUrl = u.getProtocol() + "://" + u.getAuthority();
                         } catch (Exception e) {}
                     }
                }
                
                List<ExecutionResult> newResults = pipelineService.reRunTestCases(originalTask.result.testCases, baseUrl);
                
                ProjectResult newResult = new ProjectResult();
                newResult.baseUrl = baseUrl; // Persist base URL
                newResult.testCases = originalTask.result.testCases;
                newResult.executionResults = newResults;
                newResult.apiUnderstanding = originalTask.result.apiUnderstanding;
                
                // Optional: Recalculate summary if needed, or just leave it null
                // newResult.summary = ...
                
                newTask.result = newResult;
                newTask.status = "COMPLETED";
            } catch (Exception e) {
                newTask.status = "FAILED";
                newTask.error = e.getMessage();
                e.printStackTrace();
            } finally {
                newTask.endTime = System.currentTimeMillis();
            }
        });

        return new RunResponse(newTaskId, "Re-run started successfully");
    }

    @GetMapping("/task/{taskId}")
    public TaskInfo getTaskStatus(@PathVariable("taskId") String taskId) {
        return tasks.getOrDefault(taskId, new TaskInfo(taskId, "NOT_FOUND"));
    }

    public static class RunRequest {
        public String swaggerUrl;
        public String programName;
        public String extra;
        public List<String> tags;
        public String authorization;
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
