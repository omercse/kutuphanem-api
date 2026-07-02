package com.booksystem.repository;

import com.booksystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data JPA bu metodu görür görmez "SELECT * FROM users WHERE email = ?" sorgusunu otomatik yazar
    Optional<User> findByEmail(String email);

}