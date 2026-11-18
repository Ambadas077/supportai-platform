package com.supportai;

import com.supportai.Dto.*;
import com.supportai.Entities.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepo users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public AuthRes register(@Valid @RequestBody RegisterReq req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(req.password()));
        u.setRole(Role.CUSTOMER);          // public sign-up is always a customer
        users.save(u);
        return new AuthRes(jwt.generate(u), Dto.user(u));
    }

    @PostMapping("/login")
    public AuthRes login(@Valid @RequestBody LoginReq req) {
        User u = users.findByEmail(req.email().trim().toLowerCase())
                .filter(x -> encoder.matches(req.password(), x.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!u.isActive()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        return new AuthRes(jwt.generate(u), Dto.user(u));
    }

    @GetMapping("/me")
    public UserDto me(Authentication auth) {
        return Dto.user(users.findByEmail(auth.getName()).orElseThrow());
    }
}
