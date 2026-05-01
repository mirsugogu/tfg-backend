package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BussinessRepository extends JpaRepository<Business,Long> {

}
