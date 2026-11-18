package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CommentRepo extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketOrderByIdAsc(Ticket ticket);
}
