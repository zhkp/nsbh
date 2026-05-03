# Phase 2: Tool Ecosystem + MCP Client — Design Spec

**Goal:** Expand NSBH from 2 built-in tools to a practical tool ecosystem, and integrate the MCP (Model Context Protocol) SSE client so any MCP-compatible server's tools become available without code changes.

**Architecture:** All new tools implement the existing `Tool` interface with `@NsbhTool`, auto-registering into `ToolRegistry`. A new `WorkspaceService` centralises file-path sandboxing. MCP tools are wrapped by `McpToolAdapter` and registered at startup by `McpServerRegistry` — making them fully transparent to `ChatOrchestrator`. A `NetworkSafetyUtils` helper is extracted from `HttpGetTool` so Tavily search can reuse the SSRF protection.

**Tech Stack:** Spring WebFlux, Reactor, WebClient, JUnit 5 (`@TempDir`), JaCoCo. The project already enforces 80% branch coverage at BUNDLE level via `mvn verify` — Phase 2 code must keep that gate green.

---

## File Map

### New files

| Path | Responsibility |
|------|----------------|
| `tools/NetworkSafetyUtils.java` | Static SSRF-check helpers (extracted from `HttpGetTool`) |
| `tools/WorkspaceService.java` | Resolve + sandbox-enforce all workspace paths |
| `tools/ThinkTool.java` | No-op reasoning aid: echo thought back |
| `tools/ReadFileTool.java` | Read a file from workspace |
| `tools/WriteFileTool.java` | Write a file into workspace |
| `tools/ListFilesTool.java` | List files in a workspace directory |
| `tools/TavilySearchTool.java` | Web search via Tavily API |
| `tools/ShellTool.java` | Execute allowlisted shell commands in workspace |
| `mcp/McpClient.java` | MCP client interface |
| `mcp/McpSseClient.java` | JSON-RPC 2.0 over SSE/WebClient |
| `mcp/McpToolAdapter.java` | Wraps one MCP tool as `Tool` |
| `mcp/McpServerRegistry.java` | ApplicationRunner — connects servers, registers tools |

### Modified files

| Path | Change |
|------|--------|
| `tools/HttpGetTool.java` | Delegate SSRF check to `NetworkSafetyUtils` |
| `tools/ToolService.java` | Add `executeAll()` |
| `tools/ToolRegistry.java` | Change `Map.copyOf()` to mutable maps; add `register(ToolMetadata, Tool)` |
| `config/NsbhProperties.java` | Add `Workspace`, `WebSearch`, `Shell`, `Mcp` config sections |

---

## Configuration

```yaml
nsbh:
  workspace:
    root: ${user.home}/.nsbh/workspace   # base dir for all file/shell tools

  tools:
    web-search:
      api-key: ${TAVILY_API_KEY:}
      max-results: 5
    shell:
      allowed-prefixes: [ls, cat, grep, echo, python3, node]
      max-output-bytes: 65536             # defaults to tools.maxOutputBytes if absent

  mcp:
    servers:
      - name: my-server
        transport: sse
        url: "http://localhost:3001/sse"
        headers:                          # optional
          Authorization: "Bearer ${MY_TOKEN:}"
```

`NsbhProperties` additions (all inner static classes, Java-bean style):

```
Workspace  { String root }
Tools      { ... existing fields ...
             WebSearch webSearch = new WebSearch()
             Shell shell = new Shell() }
WebSearch  { String apiKey; int maxResults = 5 }
Shell      { List<String> allowedPrefixes = []; int maxOutputBytes = 65536 }
Mcp        { List<McpServerConfig> servers = [] }
McpServerConfig { String name; String transport; String url;
                  Map<String,String> headers = {} }
```

---

## Component Designs

### NetworkSafetyUtils

Extract the `validateResolvedAddresses` and `isPrivateAddress` methods from `HttpGetTool` into a package-private `final` utility class with static methods. `HttpGetTool` and `TavilySearchTool` both call it. No Spring bean — pure static helpers.

```java
final class NetworkSafetyUtils {
    static void validateResolvedAddresses(String host) { ... }
    private static boolean isPrivateAddress(InetAddress addr) { ... }
}
```

---

### WorkspaceService

Spring `@Service`. Holds the resolved `workspaceRoot` path (creates the directory on first use if absent).

```java
Path resolve(String userPath)   // normalise + resolve under workspaceRoot
void assertSafe(Path resolved)  // throw IllegalArgumentException if outside root
```

Rejection rules:
- Input path contains `..` segments after normalisation
- Resolved path does not start with `workspaceRoot`
- (Absolute input paths are safe as long as they stay inside root after resolve)

---

### ThinkTool

```java
@NsbhTool(name = "think",
          description = "Record a reasoning step. Returns the thought unchanged.",
          schema = "{\"type\":\"object\",\"properties\":{\"thought\":{\"type\":\"string\"}},\"required\":[\"thought\"]}")
```

No permissions required. `execute(inputJson)` parses `thought`, returns `{"thought":"...","status":"ok"}`.

---

### ReadFileTool

```java
@NsbhTool(name = "read_file",
          description = "Read a file from the workspace",
          schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}",
          requiredPermissions = {"WORKSPACE_READ"})
```

1. `WorkspaceService.resolve(path)` → `assertSafe`
2. `Mono.fromCallable(() -> Files.readString(resolved)).subscribeOn(Schedulers.boundedElastic())`
3. If file size exceeds `tools.maxOutputBytes`, truncate and append `\n[truncated]`

---

### WriteFileTool

```java
@NsbhTool(name = "write_file",
          description = "Write a file into the workspace (creates parent directories)",
          schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}",
          requiredPermissions = {"WORKSPACE_WRITE"})
```

1. `WorkspaceService.resolve(path)` → `assertSafe`
2. `Files.createDirectories(resolved.getParent())`
3. `Files.writeString(resolved, content)` on `boundedElastic`
4. Returns `{"path":"...","bytes":N}`

---

### ListFilesTool

```java
@NsbhTool(name = "list_files",
          description = "List files in a workspace directory",
          schema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"recursive\":{\"type\":\"boolean\"}},\"required\":[\"path\"]}",
          requiredPermissions = {"WORKSPACE_READ"})
```

1. `WorkspaceService.resolve(path)` → `assertSafe`
2. `Files.walk` (depth 1 if `recursive=false`, unlimited if true) on `boundedElastic`
3. Returns JSON array of relative paths (relative to workspace root)
4. Caps at 500 entries to prevent runaway output

---

### TavilySearchTool

```java
@NsbhTool(name = "web_search",
          description = "Search the web using Tavily",
          schema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"max_results\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
          requiredPermissions = {"NET_HTTP"})
```

1. Check `apiKey` non-blank at construction; throw `IllegalStateException` if missing
2. `NetworkSafetyUtils.validateResolvedAddresses("api.tavily.com")` called once at startup (skip if unreachable — handled by timeout)
3. POST `https://api.tavily.com/search` with `{"api_key":..., "query":..., "max_results":N}`
4. Map response to `[{"title":"...","url":"...","snippet":"..."},...]`
5. Timeout via `WebClient` + `properties.getTools().getTimeoutMs()`

---

### ShellTool

```java
@NsbhTool(name = "shell",
          description = "Execute an allowlisted shell command in the workspace",
          schema = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}",
          requiredPermissions = {"SHELL_EXEC"})
```

Security pipeline (each check on failure throws `IllegalArgumentException`, consistent with `HttpGetTool` — `ToolService` catches it as `EXECUTION_ERROR`):

| Order | Check | Reject reason |
|-------|-------|---------------|
| 1 | Command empty | `"command is required"` |
| 2 | First token not in `allowedPrefixes` | `"command not in allowlist: <token>"` |
| 3 | Command contains any blocklist keyword (`sudo`, `rm -rf /`, `chmod 777 /`, `>/dev/`, `mkfs`) | `"blocked command pattern"` |
| 4 | Execute via `ProcessBuilder`, cwd = `workspaceRoot` | — |
| 5 | Hard timeout `shell.maxOutputBytes` millis, then `process.destroyForcibly()` | `"command timed out"` |
| 6 | stdout + stderr merged, truncated at `shell.maxOutputBytes` bytes | — |

All execution on `Schedulers.boundedElastic()`.

---

### ToolService.executeAll()

```java
public Flux<ToolExecutionResult> executeAll(String conversationId,
                                             List<ToolCallRequest> requests) {
    return Flux.fromIterable(requests)
               .flatMap(req -> execute(conversationId, req.toolName(),
                                       req.inputJson(), req.id()));
}
```

`ChatOrchestrator` does **not** switch to `executeAll` — the existing `mergeDelayError` path stays. `executeAll` is a convenience API for future callers.

---

### McpClient Interface

```java
public interface McpClient {
    Mono<List<McpToolDefinition>> listTools();
    Mono<String> callTool(String name, String inputJson);
    Mono<Void> close();
}

public record McpToolDefinition(String name, String description, String inputSchemaJson) {}
```

---

### McpSseClient

Implements `McpClient`. One `WebClient` per server instance, configured at construction with `baseUrl` and optional headers.

**Protocol**: Each call is a single HTTP POST. The server responds with an SSE stream; the client reads until it sees the JSON-RPC response event, then completes.

```
POST /sse
Content-Type: application/json

{"jsonrpc":"2.0","id":"<uuid>","method":"tools/list","params":{}}
```

Response SSE:
```
data: {"jsonrpc":"2.0","id":"<uuid>","result":{"tools":[...]}}
```

Implementation:
1. `webClient.post().uri("/sse").bodyValue(rpcRequest).retrieve().bodyToFlux(String.class)`
2. Filter `data:` lines, parse JSON-RPC response
3. Match on `id`, extract `result` or propagate `error`
4. `callTool` serialises `inputJson` (already a JSON string) into `params.input` as a `JsonNode`
5. `close()` returns `Mono.empty()` (SSE is stateless per-request — no persistent connection to close)

Error mapping: JSON-RPC error object → `LlmClientException`-style `McpException` (new class).

---

### McpToolAdapter

```java
public class McpToolAdapter implements Tool {
    private final McpClient client;
    private final McpToolDefinition definition;

    @Override
    public Mono<String> execute(String inputJson) {
        return client.callTool(definition.name(), inputJson);
    }
}
```

The adapter is **not** a Spring bean — it is created by `McpServerRegistry`.

Registration in `ToolRegistry` requires a `ToolMetadata`. `McpServerRegistry` constructs:
```java
new ToolMetadata(def.name(), def.description(), def.inputSchemaJson(), List.of())
```
(MCP tools carry no NSBH permissions — they are pre-gated by the `tools.allowed` list.)

---

### McpServerRegistry

`ApplicationRunner` + `@PreDestroy`.

```java
@Override
public void run(ApplicationArguments args) {
    for (McpServerConfig cfg : properties.getMcp().getServers()) {
        McpClient client = new McpSseClient(webClientBuilder, cfg);
        List<McpToolDefinition> tools = client.listTools().block(Duration.ofSeconds(10));
        for (McpToolDefinition def : tools) {
            ToolMetadata meta = new ToolMetadata(def.name(), def.description(),
                                                  def.inputSchemaJson(), new String[0]);
            toolRegistry.register(meta, new McpToolAdapter(client, def));
        }
        clients.add(client);
    }
}
```

If `servers` list is empty, `run()` returns immediately. If a server fails to connect, log a warning and skip it — do not abort startup.

`ToolRegistry` needs a new `register(ToolMetadata, Tool)` method to support dynamic registration (currently only reads from Spring-injected beans at construction).

---

### ToolRegistry.register()

Add to `ToolRegistry`:

```java
public void register(ToolMetadata metadata, Tool tool) {
    // add to the internal maps that findMetadata() and findTool() read from
}
```

This is the only change to `ToolRegistry` — the constructor path for `@NsbhTool` beans is untouched.

---

## Testing

| Test class | What it covers |
|------------|----------------|
| `NetworkSafetyUtilsTest` | Private IPs, loopback, valid public IP |
| `WorkspaceServiceTest` | Safe path, `..` escape, absolute-path escape |
| `ThinkToolTest` | Input → expected output |
| `ReadFileToolTest` (@TempDir) | Happy path, path escape rejected, file not found |
| `WriteFileToolTest` (@TempDir) | Write + read-back, parent dir created, path escape rejected |
| `ListFilesToolTest` (@TempDir) | Non-recursive list, recursive list, path escape rejected |
| `TavilySearchToolTest` | Happy path (ExchangeFunction mock), blank API key rejected at construction |
| `ShellToolTest` | Allowlist pass (`echo`), allowlist reject, blocklist reject, output truncation |
| `McpSseClientTest` | `listTools()` parse, `callTool()` parse (ExchangeFunction mock) |
| `McpServerRegistryTest` | Empty servers → no registration; one server → tools registered in ToolRegistry |
| `ToolServiceExecuteAllTest` | Two tools executed concurrently, both results returned |

**JaCoCo gate**: The project already enforces 80% branch coverage (BUNDLE level) in `mvn verify`. All new code must keep this gate green — no additional pom.xml changes needed.

---

## Acceptance Checklist

- [ ] `mvn test` — BUILD SUCCESS, all tests pass
- [ ] `mvn verify` — JaCoCo 80% branch coverage gate passes (existing rule, no new config needed)
- [ ] `web_search`, `read_file`, `write_file`, `list_files`, `think`, `shell` all appear in `GET /api/v1/tools`
- [ ] MCP server list empty → startup succeeds, no errors
- [ ] `tools.allowed` does not include `shell` by default (must be explicitly configured)
