package com.group.carbon.model;

import com.group.carbon.common.Types.RulingDecision;
import com.group.carbon.common.Types.RulingStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 责任裁定：控制权与设备交接不一致的时段只能待裁定，裁定后按结论纳入/排除。
 * 裁定记录不可变；重新裁定以新记录取代旧记录（旧记录标记 SUPERSEDED 并保留）。
 */
@Entity
@Table(name = "ownership_rulings")
public class OwnershipRuling {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "ruling_no", nullable = false, unique = true)
  private String rulingNo;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "equipment_code")
  private String equipmentCode;
  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;
  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RulingDecision decision;
  @Column(name = "evidence_no", nullable = false)
  private String evidenceNo;
  private String reason;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RulingStatus status = RulingStatus.ACTIVE;
  @Column(name = "supersedes_ruling_id")
  private Long supersedesRulingId;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getRulingNo() { return rulingNo; }
  public void setRulingNo(String rulingNo) { this.rulingNo = rulingNo; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getEquipmentCode() { return equipmentCode; }
  public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
  public LocalDate getPeriodStart() { return periodStart; }
  public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
  public LocalDate getPeriodEnd() { return periodEnd; }
  public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
  public RulingDecision getDecision() { return decision; }
  public void setDecision(RulingDecision decision) { this.decision = decision; }
  public String getEvidenceNo() { return evidenceNo; }
  public void setEvidenceNo(String evidenceNo) { this.evidenceNo = evidenceNo; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public RulingStatus getStatus() { return status; }
  public void setStatus(RulingStatus status) { this.status = status; }
  public Long getSupersedesRulingId() { return supersedesRulingId; }
  public void setSupersedesRulingId(Long supersedesRulingId) { this.supersedesRulingId = supersedesRulingId; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
