package com.optima.api.modules.business.controller;

import com.optima.api.modules.business.model.Business;
import com.optima.api.modules.business.repository.BussinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
@CrossOrigin(origins = "*")
public class BusinessController {
    @Autowired
    private BussinessRepository bussinessRepository;

    @GetMapping("/")
    public List<Business> getAllBusinesses(){
        return bussinessRepository.findAll();
    }

    @PostMapping("/")
    public Business createBusiness(@RequestBody Business business){
        return bussinessRepository.save(business);
    }
}
