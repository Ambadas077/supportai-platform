package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ConversationRepo extends JpaRepository<Conversation, Long> {

}
