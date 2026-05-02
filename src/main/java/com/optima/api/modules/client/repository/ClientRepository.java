package com.optima.api.modules.client.repository;

import com.optima.api.modules.client.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    // Este es el que pide el ClientModuleService
    List<Client> findByBusinessId(Long businessId);

    // Este es el que pedía el AppointmentValidator antes
    Optional<Client> findByIdAndBusinessId(Long id, Long businessId);
}
