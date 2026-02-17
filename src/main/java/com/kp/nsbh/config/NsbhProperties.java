package com.kp.nsbh.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nsbh")
public class NsbhProperties {
    private final Llm llm = new Llm();
    private final Memory memory = new Memory();
    private final Tools tools = new Tools();
    private final Permissions permissions = new Permissions();

    public Llm getLlm() {
        return llm;
    }

    public Memory getMemory() {
        return memory;
    }

    public Tools getTools() {
        return tools;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    public static class Llm {
        private String provider = "mock";
        private String modelDefault = "gpt-4.1-mini";
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private long timeoutMs = 15000;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModelDefault() {
            return modelDefault;
        }

        public void setModelDefault(String modelDefault) {
            this.modelDefault = modelDefault;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Memory {
        private int window = 20;
        private int compactAfter = 40;
        private String systemPrompt = "You are NSBH assistant. Keep answers concise and accurate.";

        public int getWindow() {
            return window;
        }

        public void setWindow(int window) {
            this.window = window;
        }

        public int getCompactAfter() {
            return compactAfter;
        }

        public void setCompactAfter(int compactAfter) {
            this.compactAfter = compactAfter;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }

    public static class Tools {
        private long timeoutMs = 3000;
        private int maxInputBytes = 8192;
        private int maxOutputBytes = 32768;
        private List<String> allowed = new ArrayList<>();

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxInputBytes() {
            return maxInputBytes;
        }

        public void setMaxInputBytes(int maxInputBytes) {
            this.maxInputBytes = maxInputBytes;
        }

        public int getMaxOutputBytes() {
            return maxOutputBytes;
        }

        public void setMaxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
        }

        public List<String> getAllowed() {
            return allowed;
        }

        public void setAllowed(List<String> allowed) {
            this.allowed = allowed;
        }
    }

    public static class Permissions {
        private List<String> granted = new ArrayList<>();

        public List<String> getGranted() {
            return granted;
        }

        public void setGranted(List<String> granted) {
            this.granted = granted;
        }
    }
}
