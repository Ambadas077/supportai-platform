package com.supportai;

import com.supportai.Entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(Role role);
    boolean existsByEmail(String email);
}
