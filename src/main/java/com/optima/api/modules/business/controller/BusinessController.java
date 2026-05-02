package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
@CrossOrigin(origins = "*")
public class BusinessController {
    @Autowired
    private BusinessRepository businessRepository;

    @GetMapping("/")
    public List<Business> getAllBusinesses(){
        return businessRepository.findAll();
    }

    @PostMapping("/")
    public Business createBusiness(@RequestBody Business business){
        return businessRepository.save(business);
    }
}
