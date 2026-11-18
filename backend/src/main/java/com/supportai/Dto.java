package com.supportai;

import com.supportai.Entities.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class Dto {
    public record RegisterReq(@NotBlank String name, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {}
    public record LoginReq(@NotBlank String email, @NotBlank String password) {}
    public record AuthRes(String token, UserDto user) {}
    public record UserDto(Long id, String name, String email, String role, boolean active) {}
    public record TicketReq(@NotBlank String subject, @NotBlank String description) {}
    public record TicketDto(Long id, String subject, String description, String summary, String category, String priority,
                            String sentiment, Double confidence, String status, UserDto customer, UserDto agent,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record CommentReq(@NotBlank String text, boolean internal) {}
    public record CommentDto(Long id, String author, String role, String text, boolean internal, LocalDateTime createdAt) {}
    public record StatusReq(@NotBlank String status) {}
    public record AssignReq(Long agentId) {}
    public record ChatReq(Long conversationId, @NotBlank String message) {}
    public record MessageDto(Long id, String sender, String content, String sources, LocalDateTime createdAt) {}
    public record ChatRes(Long conversationId, MessageDto reply, boolean escalate) {}
    public record AgentReq(@NotBlank String name, @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {}
    public record ArticleReq(@NotBlank String title, @NotBlank String content) {}

    public static UserDto user(User u) {
        return u == null ? null : new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive());
    }

    public static TicketDto ticket(Ticket t) {
        return new TicketDto(t.getId(), t.getSubject(), t.getDescription(), t.getSummary(), t.getCategory(), t.getPriority(),
                t.getSentiment(), t.getConfidence(), t.getStatus().name(), user(t.getCustomer()), user(t.getAssignedAgent()),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    public static MessageDto message(Message m) {
        return new MessageDto(m.getId(), m.getSender(), m.getContent(), m.getSources(), m.getCreatedAt());
    }
}
