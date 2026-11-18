package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface KnowledgeRepo extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findByFileName(String fileName);
}
