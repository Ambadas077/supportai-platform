package com.supportai;

import com.supportai.Dto.*;
import com.supportai.Entities.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepo users;
    private final TicketRepo tickets;
    private final ConversationRepo conversations;
    private final PasswordEncoder encoder;

    public AdminController(UserRepo users, TicketRepo tickets, ConversationRepo conversations, PasswordEncoder encoder) {
        this.users = users;
        this.tickets = tickets;
        this.conversations = conversations;
        this.encoder = encoder;
    }

    @GetMapping("/agents")
    public List<UserDto> agents() {
        return users.findByRole(Role.AGENT).stream().map(Dto::user).collect(Collectors.toList());
    }

    @PostMapping("/agents")
    public UserDto createAgent(@Valid @RequestBody AgentReq req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(req.password()));
        u.setRole(Role.AGENT);
        return Dto.user(users.save(u));
    }

    @PatchMapping("/users/{id}/toggle-active")
    public UserDto toggle(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() == Role.ADMIN) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate admin");
        u.setActive(!u.isActive());
        return Dto.user(users.save(u));
    }

    private static Map<String, Long> count(List<Ticket> list, Function<Ticket, String> f) {
        return list.stream().collect(Collectors.groupingBy(t -> {
            String k = f.apply(t);
            return k == null ? "Unknown" : k;
        }, TreeMap::new, Collectors.counting()));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Ticket> all = tickets.findAll();
        long resolved = all.stream().filter(t -> t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED).count();
        long convs = conversations.count();
        long handoffs = conversations.findAll().stream().filter(c -> "HANDOFF".equals(c.getStatus())).count();

        List<Map<String, Object>> perAgent = users.findByRole(Role.AGENT).stream().map(a -> {
            List<Ticket> mine = all.stream().filter(t -> t.getAssignedAgent() != null
                    && t.getAssignedAgent().getId().equals(a.getId())).collect(Collectors.toList());
            long done = mine.stream().filter(t -> t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("assigned", mine.size());
            m.put("resolved", done);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalTickets", all.size());
        out.put("openTickets", all.size() - resolved);
        out.put("resolvedTickets", resolved);
        out.put("totalConversations", convs);
        out.put("aiResolutionRate", convs == 0 ? 0 : Math.round((convs - handoffs) * 1000.0 / convs) / 10.0);
        out.put("escalationRate", convs == 0 ? 0 : Math.round(handoffs * 1000.0 / convs) / 10.0);
        out.put("byStatus", count(all, t -> t.getStatus().name()));
        out.put("byCategory", count(all, Ticket::getCategory));
        out.put("byPriority", count(all, Ticket::getPriority));
        out.put("bySentiment", count(all, Ticket::getSentiment));
        out.put("agents", perAgent);
        return out;
    }
}
