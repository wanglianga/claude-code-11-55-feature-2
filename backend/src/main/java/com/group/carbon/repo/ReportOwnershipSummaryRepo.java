package com.group.carbon.repo;

import com.group.carbon.model.ReportOwnershipSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReportOwnershipSummaryRepo extends JpaRepository<ReportOwnershipSummary, Long> {
  Optional<ReportOwnershipSummary> findByReportVersionId(Long reportVersionId);
}
