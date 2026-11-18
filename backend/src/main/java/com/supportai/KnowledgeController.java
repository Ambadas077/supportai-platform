package com.supportai;

import com.supportai.Dto.ArticleReq;
import com.supportai.Entities.KnowledgeDocument;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin-only knowledge base management (RAG ingestion happens in the Python AI service). */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private static final Set<String> ALLOWED = Set.of("txt", "md", "csv", "pdf", "docx");
    private final KnowledgeRepo docs;
    private final AiClient ai;

    public KnowledgeController(KnowledgeRepo docs, AiClient ai) {
        this.docs = docs;
        this.ai = ai;
    }

    private KnowledgeDocument saveDoc(String name, Map<String, Object> res) {
        int chunks = res != null && res.get("chunks_indexed") instanceof Number n ? n.intValue() : 0;
        KnowledgeDocument d = docs.findByFileName(name).orElseGet(KnowledgeDocument::new);
        d.setFileName(name);
        d.setChunks(chunks);
        return docs.save(d);
    }

    @PostMapping("/upload")
    public KnowledgeDocument upload(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allowed types: " + ALLOWED);
        try {
            return saveDoc(name, ai.ingestFile(file));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable or could not index the file");
        }
    }

    @PostMapping("/articles")
    public KnowledgeDocument article(@Valid @RequestBody ArticleReq req) {
        try {
            return saveDoc(req.title().trim(), ai.ingestText(req.title().trim(), req.content()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
        }
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument> list() {
        return docs.findAll();
    }

    @DeleteMapping("/documents/{id}")
    public void delete(@PathVariable Long id) {
        docs.findById(id).ifPresent(d -> {
            ai.deleteSource(d.getFileName());
            docs.delete(d);
        });
    }
}
