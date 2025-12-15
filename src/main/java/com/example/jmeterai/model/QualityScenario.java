package com.example.jmeterai.model;

public enum QualityScenario {
    HAPPY_PATH("基本功能"),
    PARAM_INTEGRITY("参数完整性"),
    ABNORMAL_INPUT("异常输入");

    private final String description;

    QualityScenario(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
