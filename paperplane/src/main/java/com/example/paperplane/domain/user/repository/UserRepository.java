package com.example.paperplane.domain.user.repository;

import com.example.paperplane.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoId(String kakaoId);
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
}
