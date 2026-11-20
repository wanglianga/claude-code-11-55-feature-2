package com.group.carbon.repo;

import com.group.carbon.common.Types.EventStatus;
import com.group.carbon.model.OwnershipEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OwnershipEventRepo extends JpaRepository<OwnershipEvent, Long> {
  List<OwnershipEvent> findAllByOrderByEffectiveDateAscIdAsc();
  List<OwnershipEvent> findByPlantIdOrderByEffectiveDateAscIdAsc(Long plantId);
  List<OwnershipEvent> findByStatusOrderByEffectiveDateAscIdAsc(EventStatus status);
  List<OwnershipEvent> findByPlantIdAndEquipmentCodeOrderByEffectiveDateAscIdAsc(Long plantId, String equipmentCode);
  List<OwnershipEvent> findByPlantIdAndEquipmentCodeIsNullOrderByEffectiveDateAscIdAsc(Long plantId);
  List<OwnershipEvent> findBySupersedesEventId(Long supersedesEventId);
}
