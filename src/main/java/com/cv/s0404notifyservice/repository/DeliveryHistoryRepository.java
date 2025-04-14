package com.cv.s0404notifyservice.repository;

import com.cv.s0402notifyservicepojo.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, String>,
        JpaSpecificationExecutor<DeliveryHistory> {
}
