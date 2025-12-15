package com.example.jmeterai.service;

import com.example.jmeterai.client.DashscopeClient;
import com.example.jmeterai.client.DeepseekClient;
import com.example.jmeterai.client.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    @Value("${LLM_PROVIDER:}")
    private String provider;

    @Value("${DEEPSEEK_API_KEY:}")
    private String deepseekKey;
    @Value("${DEEPSEEK_BASE_URL:https://api.deepseek.com}")
    private String deepseekBase;
    @Value("${DEEPSEEK_MODEL:deepseek-chat}")
    private String deepseekModel;

    @Value("${GEMINI_API_KEY:}")
    private String geminiKey;
    @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}")
    private String geminiBase;
    @Value("${GEMINI_MODEL:gemini-3-pro-preview}")
    private String geminiModel;

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashKey;
    @Value("${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String dashBase;
    @Value("${DASHSCOPE_MODEL:qwen-plus}")
    private String dashModel;

    public String callLlm(String system, String user) throws Exception {
        String p = provider.toLowerCase();
        boolean hasDeepseek = !deepseekKey.isEmpty();
        boolean hasGemini = !geminiKey.isEmpty();
        boolean hasDash = !dashKey.isEmpty();

        if (p.equals("deepseek") || (p.isEmpty() && hasDeepseek)) {
            DeepseekClient client = new DeepseekClient(deepseekKey, deepseekBase, deepseekModel);
            return client.chat(system, user);
        } else if (p.equals("gemini") || (p.isEmpty() && hasGemini)) {
            GeminiClient client = new GeminiClient(geminiKey, geminiBase, geminiModel);
            return client.chat(system, user);
        } else if (p.equals("dashscope") || (p.isEmpty() && hasDash)) {
            DashscopeClient client = new DashscopeClient(dashKey, dashBase, dashModel);
            return client.chat(system, user);
        }
        throw new RuntimeException("未配置可用的LLM: 请设置 LLM_PROVIDER 以及对应密钥");
    }
}
