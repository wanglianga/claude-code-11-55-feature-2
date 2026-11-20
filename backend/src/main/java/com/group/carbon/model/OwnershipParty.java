package com.group.carbon.model;

import com.group.carbon.common.Types.PartyType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 责任主体：集团内法人/工厂，或集团外原股东/出租方。 */
@Entity
@Table(name = "ownership_parties")
public class OwnershipParty {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true)
  private String code;
  @Column(nullable = false)
  private String name;
  @Enumerated(EnumType.STRING)
  @Column(name = "party_type", nullable = false)
  private PartyType partyType;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public PartyType getPartyType() { return partyType; }
  public void setPartyType(PartyType partyType) { this.partyType = partyType; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
