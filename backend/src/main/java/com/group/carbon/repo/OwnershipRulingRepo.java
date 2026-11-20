package com.group.carbon.repo;

import com.group.carbon.common.Types.RulingStatus;
import com.group.carbon.model.OwnershipRuling;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OwnershipRulingRepo extends JpaRepository<OwnershipRuling, Long> {
  List<OwnershipRuling> findByStatusOrderByPeriodStartAscIdAsc(RulingStatus status);
  List<OwnershipRuling> findByPlantIdOrderByPeriodStartAscIdAsc(Long plantId);
  List<OwnershipRuling> findByPlantIdAndStatusOrderByPeriodStartAscIdAsc(Long plantId, RulingStatus status);
}
