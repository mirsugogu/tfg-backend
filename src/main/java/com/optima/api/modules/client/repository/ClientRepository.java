package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByBusinessId(Long businessId);
}
