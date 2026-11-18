package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MessageRepo extends JpaRepository<Message, Long> {
    List<Message> findByConversationOrderByIdAsc(Conversation conversation);
}
