package com.optima.api.modules.business.service;

import com.optima.api.modules.business.model.Service;
import com.optima.api.modules.business.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@org.springframework.stereotype.Service
public class ServiceModuleService {

    @Autowired
    private ServiceRepository serviceRepository;

    public List<Service> getServicesByBusiness(Long businessId) {
        return serviceRepository.findByBusinessId(businessId);
    }

    public Service createService(Service service) {
        // Aquí podrías añadir lógica extra (ej. validar precios)
        return serviceRepository.save(service);
    }
}