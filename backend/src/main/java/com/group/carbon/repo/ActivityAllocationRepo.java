package com.group.carbon.repo;

import com.group.carbon.common.Types.AllocationClass;
import com.group.carbon.model.ActivityAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityAllocationRepo extends JpaRepository<ActivityAllocation, Long> {
  List<ActivityAllocation> findByRunIdOrderById(Long runId);
  List<ActivityAllocation> findByRunIdAndClassificationOrderById(Long runId, AllocationClass classification);
  List<ActivityAllocation> findBySourceRowIdOrderById(Long sourceRowId);
  List<ActivityAllocation> findByResultIdOrderById(Long resultId);
  long countByRunIdAndClassification(Long runId, AllocationClass classification);
}
