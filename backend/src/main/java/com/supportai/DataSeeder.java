package com.supportai;

import com.supportai.Entities.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates demo accounts on first start. Change these passwords before any real deployment. */
@Component
public class DataSeeder implements CommandLineRunner {
    private final UserRepo users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepo users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    private void seed(String name, String email, String pass, Role role) {
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(pass));
        u.setRole(role);
        users.save(u);
    }

    @Override
    public void run(String... args) {
        seed("Admin", "admin@supportai.com", "Admin@123", Role.ADMIN);
        seed("Agent Asha", "agent@supportai.com", "Agent@123", Role.AGENT);
        seed("Agent Ravi", "agent2@supportai.com", "Agent@123", Role.AGENT);
        seed("Demo Customer", "customer@supportai.com", "Customer@123", Role.CUSTOMER);
    }
}
