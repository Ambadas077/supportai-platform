package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface TicketRepo extends JpaRepository<Ticket, Long> {
    List<Ticket> findAllByOrderByCreatedAtDesc();
    List<Ticket> findByCustomerOrderByCreatedAtDesc(User customer);
    long countByAssignedAgentAndStatusIn(User agent, Collection<TicketStatus> statuses);
}
