package com.cv.s0404notifyservice.repository;

import com.cv.s0402notifyservicepojo.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, String>,
        JpaSpecificationExecutor<Recipient> {
}
