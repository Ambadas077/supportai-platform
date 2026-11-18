package com.supportai;

import com.supportai.Entities.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {
    private static final List<TicketStatus> ACTIVE = List.of(TicketStatus.OPEN, TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS, TicketStatus.PENDING, TicketStatus.ESCALATED);
    private final TicketRepo tickets;
    private final UserRepo users;
    private final AiClient ai;

    public TicketService(TicketRepo tickets, UserRepo users, AiClient ai) {
        this.tickets = tickets;
        this.users = users;
        this.ai = ai;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    /** Creates a ticket, runs AI classification + sentiment, and auto-assigns the least-loaded active agent. */
    public Ticket create(User customer, String subject, String description) {
        String text = subject + ". " + description;
        Map<String, Object> cls = ai.classify(text);
        Map<String, Object> sen = ai.sentiment(text);

        Ticket t = new Ticket();
        t.setCustomer(customer);
        t.setSubject(subject);
        t.setDescription(description);
        t.setCategory(str(cls.get("category"), "General"));
        t.setPriority(str(cls.get("priority"), "Medium"));
        t.setSentiment(str(sen.get("sentiment"), "Neutral"));
        if (cls.get("confidence") instanceof Number n) t.setConfidence(n.doubleValue());
        t.setSummary(description.length() > 200
                ? str(ai.summarize(List.of(description)).get("summary"), description) : description);

        User agent = pickAgent();
        if (agent != null) {
            t.setAssignedAgent(agent);
            t.setStatus(TicketStatus.ASSIGNED);
        }
        return tickets.save(t);
    }

    public User pickAgent() {
        return users.findByRole(Role.AGENT).stream().filter(User::isActive)
                .min(Comparator.comparingLong(a -> tickets.countByAssignedAgentAndStatusIn(a, ACTIVE)))
                .orElse(null);
    }

    public void touch(Ticket t) {
        t.setUpdatedAt(LocalDateTime.now());
        tickets.save(t);
    }
}
