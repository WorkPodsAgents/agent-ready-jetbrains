package ai.workpods.agentready;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.progress.ProgressIndicator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A minimal client for the hosted Agent Ready MCP server. It speaks MCP's Streamable HTTP
 * transport: initialize, then one tools/call in that session.
 */
final class AgentReadyClient {
    private static final URI ENDPOINT = URI.create("https://mcp.workpods.ai/mcp");
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final String clientVersion;

    AgentReadyClient(HttpClient http, String clientVersion) {
        this.http = http;
        this.clientVersion = clientVersion;
    }

    CheckResult run(String tool, String url, ProgressIndicator indicator) throws CheckFailed {
        Reply opened = post(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "agent-ready-jetbrains", "version", clientVersion))),
                null);
        if (opened.session == null) {
            throw new CheckFailed("Agent Ready did not open a session; try again later.");
        }
        indicator.checkCanceled();
        post(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), opened.session);
        indicator.checkCanceled();
        Reply called = post(Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/call",
                "params", Map.of("name", tool, "arguments", Map.of("url", url))),
                opened.session);
        return result(called.body);
    }

    private static CheckResult result(@Nullable JsonObject body) throws CheckFailed {
        if (body == null) throw new CheckFailed("Agent Ready sent an empty answer; try again later.");
        if (body.has("error")) {
            throw new CheckFailed(body.getAsJsonObject("error").get("message").getAsString());
        }
        JsonObject result = body.getAsJsonObject("result");
        String text = null;
        JsonArray content = result == null ? null : result.getAsJsonArray("content");
        if (content != null) {
            for (JsonElement part : content) {
                JsonObject object = part.getAsJsonObject();
                if ("text".equals(object.get("type").getAsString())) {
                    text = object.get("text").getAsString();
                    break;
                }
            }
        }
        if (text == null) throw new CheckFailed("Agent Ready sent an empty answer; try again later.");
        // A refused or failed check comes back as one plain sentence.
        if (result.has("isError") && result.get("isError").getAsBoolean()) throw new CheckFailed(text);
        JsonObject envelope = JsonParser.parseString(text).getAsJsonObject();
        JsonElement report = envelope.get("reportUrl");
        return new CheckResult(
                envelope.get("summary").getAsString(),
                report == null || report.isJsonNull() ? null : report.getAsString(),
                envelope.get("result").toString());
    }

    private Reply post(Map<String, Object> message, @Nullable String session) throws CheckFailed {
        HttpRequest.Builder request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("content-type", "application/json")
                .header("accept", "application/json, text/event-stream")
                .header("mcp-protocol-version", PROTOCOL_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(message)));
        if (session != null) request.header("mcp-session-id", session);
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException error) {
            throw new CheckFailed("Could not reach Agent Ready; check your connection and try again.");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CheckFailed("The check was cancelled.");
        }
        int status = response.statusCode();
        if (status == 429) {
            throw new CheckFailed("Agent Ready is busy or your daily limit is used up; try again later.");
        }
        if (status != 202 && (status < 200 || status >= 300)) {
            throw new CheckFailed("Agent Ready answered HTTP " + status + "; try again later.");
        }
        String next = response.headers().firstValue("mcp-session-id").orElse(session);
        return new Reply(next, parse(response.body()));
    }

    /** The answer arrives as plain JSON or as server-sent events; either way take the JSON-RPC reply. */
    private static @Nullable JsonObject parse(String body) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("{")) return JsonParser.parseString(trimmed).getAsJsonObject();
        String last = null;
        for (String line : trimmed.split("\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (data.startsWith("{")) last = data;
            }
        }
        return last == null ? null : JsonParser.parseString(last).getAsJsonObject();
    }

    private record Reply(@Nullable String session, @Nullable JsonObject body) {}

    /** An expected failure, with a sentence the person can act on. */
    static final class CheckFailed extends Exception {
        CheckFailed(String message) {
            super(message);
        }
    }
}
