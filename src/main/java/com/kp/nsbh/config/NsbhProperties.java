package com.kp.nsbh.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nsbh")
public class NsbhProperties {
    private final Llm llm = new Llm();
    private final Memory memory = new Memory();
    private final Tools tools = new Tools();
    private final Permissions permissions = new Permissions();
    private final Agent agent = new Agent();
    private final Workspace workspace = new Workspace();
    private final Mcp mcp = new Mcp();

    public Llm getLlm() { return llm; }
    public Memory getMemory() { return memory; }
    public Tools getTools() { return tools; }
    public Permissions getPermissions() { return permissions; }
    public Agent getAgent() { return agent; }
    public Workspace getWorkspace() { return workspace; }
    public Mcp getMcp() { return mcp; }

    public static class Llm {
        private String provider = "mock";
        private String modelDefault = "gpt-4.1-mini";
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private long timeoutMs = 15000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModelDefault() { return modelDefault; }
        public void setModelDefault(String modelDefault) { this.modelDefault = modelDefault; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class Memory {
        private int window = 20;
        private int compactAfter = 40;
        private String systemPrompt = "You are NSBH assistant. Keep answers concise and accurate.";

        public int getWindow() { return window; }
        public void setWindow(int window) { this.window = window; }
        public int getCompactAfter() { return compactAfter; }
        public void setCompactAfter(int compactAfter) { this.compactAfter = compactAfter; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    }

    public static class Tools {
        private long timeoutMs = 3000;
        private int maxInputBytes = 8192;
        private int maxOutputBytes = 32768;
        private List<String> allowed = new ArrayList<>();
        private final WebSearch webSearch = new WebSearch();
        private final Shell shell = new Shell();

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxInputBytes() { return maxInputBytes; }
        public void setMaxInputBytes(int maxInputBytes) { this.maxInputBytes = maxInputBytes; }
        public int getMaxOutputBytes() { return maxOutputBytes; }
        public void setMaxOutputBytes(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }
        public List<String> getAllowed() { return allowed; }
        public void setAllowed(List<String> allowed) { this.allowed = allowed; }
        public WebSearch getWebSearch() { return webSearch; }
        public Shell getShell() { return shell; }
    }

    public static class WebSearch {
        private String apiKey = "";
        private int maxResults = 5;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }

    public static class Shell {
        private List<String> allowedPrefixes = new ArrayList<>();
        private int maxOutputBytes = 65536;

        public List<String> getAllowedPrefixes() { return allowedPrefixes; }
        public void setAllowedPrefixes(List<String> allowedPrefixes) { this.allowedPrefixes = allowedPrefixes; }
        public int getMaxOutputBytes() { return maxOutputBytes; }
        public void setMaxOutputBytes(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }
    }

    public static class Permissions {
        private List<String> granted = new ArrayList<>();

        public List<String> getGranted() { return granted; }
        public void setGranted(List<String> granted) { this.granted = granted; }
    }

    public static class Agent {
        private int maxToolRounds = 10;

        public int getMaxToolRounds() { return maxToolRounds; }
        public void setMaxToolRounds(int maxToolRounds) { this.maxToolRounds = maxToolRounds; }
    }

    public static class Workspace {
        private String root = System.getProperty("user.home") + "/.nsbh/workspace";

        public String getRoot() { return root; }
        public void setRoot(String root) { this.root = root; }
    }

    public static class Mcp {
        private List<McpServerConfig> servers = new ArrayList<>();

        public List<McpServerConfig> getServers() { return servers; }
        public void setServers(List<McpServerConfig> servers) { this.servers = servers; }
    }

    public static class McpServerConfig {
        private String name;
        private String transport = "sse";
        private String url;
        private Map<String, String> headers = new LinkedHashMap<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    }
}
