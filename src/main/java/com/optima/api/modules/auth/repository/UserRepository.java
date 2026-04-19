package com.optima.api.modules.auth.repository;

import org.springframework.stereotype.Service;
/*import com.optima.api.modules.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 */
@Service
public class UserRepository {
    // método de prueba
    public boolean existsByEmail(String email){
        return true;
    }
}
/*
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // Spring Data JPA genera la consulta automáticamente por el nombre del método
    boolean existsByEmail(String email);
}*/