package com.supportai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Calls the Python FastAPI AI service. Every call has a safe fallback so the platform keeps working if AI is down. */
@Component
public class AiClient {
    private final RestTemplate rest;
    private final String base;

    public AiClient(@Value("${app.ai.url}") String base) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(120000);
        this.rest = new RestTemplate(f);
        this.base = base;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, Map<String, Object> fallback) {
        try {
            Map<String, Object> r = rest.postForObject(base + path, body, Map.class);
            return r != null ? r : fallback;
        } catch (Exception e) {
            System.err.println("[AiClient] Failed calling " + base + path);
            e.printStackTrace();
            return fallback;
        }
    }

    public Map<String, Object> chat(String question) {
        return post("/api/ai/chat", Map.of("org_id", 1, "question", question),
                Map.of("answer", "The AI assistant is unavailable right now. I will connect you with a support agent.",
                        "escalate", true, "sources", List.of()));
    }

    public Map<String, Object> classify(String text) {
        return post("/api/ai/classify", Map.of("text", text),
                Map.of("category", "General", "priority", "Medium", "department", "Support",
                        "confidence", 0.0, "requiresHuman", true));
    }

    public Map<String, Object> sentiment(String text) {
        return post("/api/ai/sentiment", Map.of("text", text), Map.of("sentiment", "Neutral", "score", 0.0));
    }

    public Map<String, Object> summarize(List<String> messages) {
        return post("/api/ai/summarize", Map.of("messages", messages), Map.of("summary", ""));
    }

    public Map<String, Object> suggestReply(String conversation) {
        return post("/api/ai/suggest-reply", Map.of("org_id", 1, "conversation", conversation),
                Map.of("reply", "Thank you for contacting us. We are looking into your issue and will update you shortly."));
    }

    // ---- knowledge base (no fallback: admin must know if indexing failed) ----
    @SuppressWarnings("unchecked")
    public Map<String, Object> ingestFile(MultipartFile file) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("org_id", "1");
        final String name = file.getOriginalFilename();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return name; }
        });
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForObject(base + "/api/ai/ingest-file", new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> ingestText(String source, String text) {
        return rest.postForObject(base + "/api/ai/ingest", Map.of("org_id", 1, "source", source, "text", text), Map.class);
    }

    public void deleteSource(String source) {
        try {
            rest.postForObject(base + "/api/ai/delete-source", Map.of("org_id", 1, "source", source), Map.class);
        } catch (Exception ignored) { }
    }
}
