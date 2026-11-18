package com.supportai;

import com.supportai.Dto.*;
import com.supportai.Entities.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketRepo tickets;
    private final CommentRepo comments;
    private final UserRepo users;
    private final TicketService service;
    private final AiClient ai;

    public TicketController(TicketRepo tickets, CommentRepo comments, UserRepo users, TicketService service, AiClient ai) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.service = service;
        this.ai = ai;
    }

    private User me(Authentication a) {
        return users.findByEmail(a.getName()).orElseThrow();
    }

    private boolean staff(User u) {
        return u.getRole() != Role.CUSTOMER;
    }

    private Ticket load(Long id, User u) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!staff(u) && !t.getCustomer().getId().equals(u.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your ticket");
        return t;
    }

    @PostMapping
    public TicketDto create(@Valid @RequestBody TicketReq req, Authentication auth) {
        return Dto.ticket(service.create(me(auth), req.subject().trim(), req.description().trim()));
    }

    @GetMapping
    public List<TicketDto> list(Authentication auth) {
        User u = me(auth);
        List<Ticket> all = switch (u.getRole()) {
            case CUSTOMER -> tickets.findByCustomerOrderByCreatedAtDesc(u);
            case ADMIN -> tickets.findAllByOrderByCreatedAtDesc();
            case AGENT -> tickets.findAllByOrderByCreatedAtDesc().stream()
                    .filter(t -> t.getAssignedAgent() == null || t.getAssignedAgent().getId().equals(u.getId()))
                    .collect(Collectors.toList());
        };
        return all.stream().map(Dto::ticket).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id, Authentication auth) {
        User u = me(auth);
        Ticket t = load(id, u);
        List<CommentDto> cs = comments.findByTicketOrderByIdAsc(t).stream()
                .filter(c -> staff(u) || !c.isInternal())
                .map(c -> new CommentDto(c.getId(), c.getAuthor().getName(), c.getAuthor().getRole().name(),
                        c.getText(), c.isInternal(), c.getCreatedAt()))
                .collect(Collectors.toList());
        return Map.of("ticket", Dto.ticket(t), "comments", cs);
    }

    @PatchMapping("/{id}/status")
    public TicketDto status(@PathVariable Long id, @Valid @RequestBody StatusReq req, Authentication auth) {
        User u = me(auth);
        Ticket t = load(id, u);
        TicketStatus next;
        try {
            next = TicketStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        // customers may only re-open their own resolved/closed tickets
        if (!staff(u) && !(next == TicketStatus.OPEN
                && (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        t.setStatus(next);
        service.touch(t);
        return Dto.ticket(t);
    }

    @PatchMapping("/{id}/assign")
    public TicketDto assign(@PathVariable Long id, @RequestBody AssignReq req, Authentication auth) {
        User u = me(auth);
        if (!staff(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        Ticket t = load(id, u);
        User agent = users.findById(req.agentId()).filter(a -> a.getRole() == Role.AGENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent not found"));
        t.setAssignedAgent(agent);
        if (t.getStatus() == TicketStatus.OPEN) t.setStatus(TicketStatus.ASSIGNED);
        service.touch(t);
        return Dto.ticket(t);
    }

    @PostMapping("/{id}/comments")
    public CommentDto comment(@PathVariable Long id, @Valid @RequestBody CommentReq req, Authentication auth) {
        User u = me(auth);
        Ticket t = load(id, u);
        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setAuthor(u);
        c.setText(req.text().trim());
        c.setInternal(staff(u) && req.internal());       // customers can never post internal notes
        comments.save(c);
        service.touch(t);
        return new CommentDto(c.getId(), u.getName(), u.getRole().name(), c.getText(), c.isInternal(), c.getCreatedAt());
    }

    /** AI drafts a reply; the agent must review and send it. Nothing is sent automatically. */
    @PostMapping("/{id}/suggest-reply")
    public Map<String, Object> suggest(@PathVariable Long id, Authentication auth) {
        User u = me(auth);
        if (!staff(u)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        Ticket t = load(id, u);
        StringBuilder sb = new StringBuilder(t.getSubject()).append(". ").append(t.getDescription());
        comments.findByTicketOrderByIdAsc(t).stream().filter(c -> !c.isInternal())
                .forEach(c -> sb.append("\n").append(c.getText()));
        return ai.suggestReply(sb.toString());
    }
}
