package com.supportai;

import com.supportai.Dto.*;
import com.supportai.Entities.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ConversationRepo conversations;
    private final MessageRepo messages;
    private final UserRepo users;
    private final AiClient ai;
    private final TicketService ticketService;

    public ChatController(ConversationRepo conversations, MessageRepo messages, UserRepo users,
                          AiClient ai, TicketService ticketService) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.ai = ai;
        this.ticketService = ticketService;
    }

    private Conversation owned(Long id, User u) {
        Conversation c = conversations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!c.getCustomer().getId().equals(u.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your conversation");
        return c;
    }

    private Message save(Conversation c, String sender, String content, String sources) {
        Message m = new Message();
        m.setConversation(c);
        m.setSender(sender);
        m.setContent(content);
        m.setSources(sources);
        return messages.save(m);
    }

    @PostMapping("/message")
    public ChatRes send(@Valid @RequestBody ChatReq req, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        Conversation conv;
        if (req.conversationId() == null) {
            conv = new Conversation();
            conv.setCustomer(u);
            conv = conversations.save(conv);
        } else {
            conv = owned(req.conversationId(), u);
        }
        save(conv, "CUSTOMER", req.message().trim(), null);

        Map<String, Object> r = ai.chat(req.message().trim());
        String answer = String.valueOf(r.get("answer"));
        boolean escalate = Boolean.TRUE.equals(r.get("escalate"));
        String sources = r.get("sources") instanceof List<?> l
                ? l.stream().map(String::valueOf).collect(Collectors.joining(", ")) : "";
        Message reply = save(conv, "AI", answer, sources);
        return new ChatRes(conv.getId(), Dto.message(reply), escalate);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> history(@PathVariable Long id, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        return messages.findByConversationOrderByIdAsc(owned(id, u)).stream().map(Dto::message).collect(Collectors.toList());
    }

    /** Escalate to a human: AI summarises the chat, a ticket is created, classified and assigned. */
    @PostMapping("/{id}/handoff")
    public TicketDto handoff(@PathVariable Long id, Authentication auth) {
        User u = users.findByEmail(auth.getName()).orElseThrow();
        Conversation conv = owned(id, u);
        if (conv.getTicketId() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Already escalated");
        List<Message> msgs = messages.findByConversationOrderByIdAsc(conv);
        List<String> customerMsgs = msgs.stream().filter(m -> m.getSender().equals("CUSTOMER"))
                .map(Message::getContent).collect(Collectors.toList());
        String transcript = msgs.stream().map(m -> m.getSender() + ": " + m.getContent()).collect(Collectors.joining("\n"));
        String summary = String.valueOf(ai.summarize(customerMsgs).get("summary"));
        String first = customerMsgs.isEmpty() ? "Chat escalation" : customerMsgs.get(0);
        String subject = "Chat escalation: " + (first.length() > 60 ? first.substring(0, 60) + "..." : first);
        Ticket t = ticketService.create(u, subject, "AI summary: " + summary + "\n\nTranscript:\n" + transcript);
        conv.setStatus("HANDOFF");
        conv.setTicketId(t.getId());
        conversations.save(conv);
        return Dto.ticket(t);
    }
}
