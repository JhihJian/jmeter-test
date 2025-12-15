package com.example.jmeterai.controller;

import com.example.jmeterai.model.ProjectResult;
import com.example.jmeterai.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
public class JMeterAiController {

    @Autowired
    private PipelineService pipelineService;

    @PostMapping("/run")
    public ProjectResult runProject(@RequestBody RunRequest request) throws Exception {
        return pipelineService.runPipeline(request.swaggerUrl, request.programName, request.extra);
    }

    public static class RunRequest {
        public String swaggerUrl;
        public String programName;
        public String extra;
    }
}
