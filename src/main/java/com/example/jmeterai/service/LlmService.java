package com.example.jmeterai.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
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
        
        String apiKey = "";
        String baseUrl = "";
        String model = "";

        if (p.equals("deepseek") || (p.isEmpty() && hasDeepseek)) {
            apiKey = deepseekKey;
            baseUrl = deepseekBase;
            model = deepseekModel;
        } else if (p.equals("gemini") || (p.isEmpty() && hasGemini)) {
            apiKey = geminiKey;
            baseUrl = geminiBase;
            model = geminiModel;
        } else if (p.equals("dashscope") || (p.isEmpty() && hasDash)) {
            apiKey = dashKey;
            baseUrl = dashBase;
            model = dashModel;
        } else {
             throw new RuntimeException("未配置可用的LLM: 请设置 LLM_PROVIDER 以及对应密钥");
        }

         log.info("Calling LLM: provider={}, model={}, baseUrl={},system length={},user length={}", p, model, baseUrl,system.length(),user.length());

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(300))
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addMessage(ChatCompletionMessageParam.ofSystem(com.openai.models.ChatCompletionSystemMessageParam.builder().content(system).build()))
                .addMessage(ChatCompletionMessageParam.ofUser(com.openai.models.ChatCompletionUserMessageParam.builder().content(user).build()))
                .model(model)
                .build();

        ChatCompletion chatCompletion = client.chat().completions().create(params);
        
        if (chatCompletion.choices().isEmpty()) {
            throw new RuntimeException("LLM returned no choices");
        }
        
        return chatCompletion.choices().get(0).message().content().orElse("");
    }
}
