package com.group.carbon.repo;

import com.group.carbon.model.OwnershipParty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnershipPartyRepo extends JpaRepository<OwnershipParty, Long> {
  Optional<OwnershipParty> findByCode(String code);
}
