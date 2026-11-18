package com.supportai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

public class Entities {

    public enum Role { CUSTOMER, AGENT, ADMIN }

    public enum TicketStatus { OPEN, ASSIGNED, IN_PROGRESS, PENDING, ESCALATED, RESOLVED, CLOSED }

    @Entity @Table(name = "users") @Getter @Setter
    public static class User {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private String name;
        @Column(unique = true, nullable = false) private String email;
        @Column(nullable = false) private String password;
        @Enumerated(EnumType.STRING) private Role role;
        private boolean active = true;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity @Table(name = "tickets") @Getter @Setter
    public static class Ticket {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private String subject;
        @Column(columnDefinition = "TEXT") private String description;
        @Column(columnDefinition = "TEXT") private String summary;
        private String category;
        private String priority;
        private String sentiment;
        private Double confidence;
        @Enumerated(EnumType.STRING) private TicketStatus status = TicketStatus.OPEN;
        @ManyToOne private User customer;
        @ManyToOne private User assignedAgent;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
    }

    @Entity @Table(name = "ticket_comments") @Getter @Setter
    public static class TicketComment {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        @ManyToOne private Ticket ticket;
        @ManyToOne private User author;
        @Column(columnDefinition = "TEXT") private String text;
        private boolean internal;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity @Table(name = "conversations") @Getter @Setter
    public static class Conversation {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        @ManyToOne private User customer;
        private String status = "AI";          // AI | HANDOFF
        private Long ticketId;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity @Table(name = "messages") @Getter @Setter
    public static class Message {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        @ManyToOne private Conversation conversation;
        private String sender;                 // CUSTOMER | AI
        @Column(columnDefinition = "TEXT") private String content;
        private String sources;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Entity @Table(name = "knowledge_documents") @Getter @Setter
    public static class KnowledgeDocument {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
        private String fileName;
        private int chunks;
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}
