package org.china;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class OllamaClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ExecutorService ioPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ollama-io");
        t.setDaemon(true);
        return t;
    });

    public String defaultServer() {
        String env = System.getenv("OLLAMA_HOST");
        if (env == null || env.isBlank()) return "http://127.0.0.1:11434";
        if (!env.startsWith("http")) env = "http://" + env;
        if (env.endsWith("/")) env = env.substring(0, env.length()-1);
        return env;
    }

    public void ping(String server, Consumer<Boolean> onResult) {
        String base = normalize(server);
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(base + "/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> resp.statusCode() == 200)
                .exceptionally(ex -> false)
                .thenAccept(onResult);
    }

    public StreamHandle streamGenerate(String server, String model, String prompt,
                                       Consumer<String> onToken,
                                       Runnable onDone,
                                       Consumer<Throwable> onError) {
        String base = normalize(server);
        String body = "{\"model\":\"" + escape(model) + "\",\"prompt\":\"" + escape(prompt) + "\",\"stream\":true}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/generate"))
                // Removido timeout zero (PT0S) que dá IllegalArgumentException no Java 17.
                // Sem timeout aqui: o streaming segue até o servidor sinalizar "done".
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        AtomicBoolean cancelled = new AtomicBoolean(false);

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = resp.body();
                     BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while (!cancelled.get() && (line = br.readLine()) != null) {
                        if (line.isBlank()) continue;
                        try {
                            JsonNode n = mapper.readTree(line);
                            if (n.has("response")) {
                                String token = n.get("response").asText();
                                onToken.accept(token);
                            }
                            if (n.has("done") && n.get("done").asBoolean(false)) break;
                        } catch (Exception parse) {
                            // ignora linhas não-JSON
                        }
                    }
                }
                onDone.run();
            } catch (Exception e) {
                if (!cancelled.get()) onError.accept(e);
            }
        }, ioPool);

        return new StreamHandle(() -> cancelled.set(true));
    }

    public static class StreamHandle {
        private final Runnable cancel;
        private StreamHandle(Runnable cancel) { this.cancel = cancel; }
        public void cancel() { cancel.run(); }
    }

    private static String normalize(String server) {
        Objects.requireNonNull(server, "server");
        server = server.trim();
        if (server.isBlank()) server = "http://127.0.0.1:11434";
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            server = "http://" + server;
        }
        if (server.endsWith("/")) server = server.substring(0, server.length()-1);
        return server;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
