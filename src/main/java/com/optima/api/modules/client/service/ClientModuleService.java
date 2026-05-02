package com.optima.api.modules.client.service;

import com.optima.api.modules.client.model.Client;
import com.optima.api.modules.client.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientModuleService {
    @Autowired
    private ClientRepository clientRepository;

    public List<Client> getByBusiness(Long businessId) {
        return clientRepository.findByBusinessId(businessId);
    }

    public Client create(Client client) {
        return clientRepository.save(client);
    }

    public Client getById(Long id) {
        return clientRepository.findById(id).orElse(null);
    }
}
