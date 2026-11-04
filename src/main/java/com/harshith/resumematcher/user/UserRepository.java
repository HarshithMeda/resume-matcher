package com.harshith.resumematcher.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** SELECT ... FOR UPDATE: makes one user's concurrent uploads take turns (for the 5-resume limit). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
