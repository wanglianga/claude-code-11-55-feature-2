package com.group.carbon.model;

import com.group.carbon.common.Types.EvidenceType;
import com.group.carbon.common.Types.EventStatus;
import com.group.carbon.common.Types.OwnershipEventType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 不可变责任事件：收购、剥离、设备移交、停止计量、租赁约定、裁定、追溯更正。
 * 登记后不更新、不删除；追溯更正以 CORRECTION 事件指向被更正事件，
 * 裁定以 RULING 事件/裁定记录覆盖冲突时段，事件行全部保留以支持历史复演。
 */
@Entity
@Table(name = "ownership_events")
public class OwnershipEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "event_no", nullable = false, unique = true)
  private String eventNo;
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private OwnershipEventType eventType;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "equipment_code")
  private String equipmentCode;
  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", nullable = false)
  private EvidenceType evidenceType;
  @Column(name = "evidence_no", nullable = false)
  private String evidenceNo;
  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;
  private String counterparty;
  /** 结构化参数 JSON：目标主体 id/编码、计量安排、更正说明等 */
  private String payload;
  /** 相反法律证据：指向冲突事件；冲突时段只能待裁定，不能被多数票覆盖 */
  @Column(name = "conflict_with")
  private Long conflictWith;
  /** 追溯更正事件指向被更正的事件 */
  @Column(name = "supersedes_event_id")
  private Long supersedesEventId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventStatus status = EventStatus.ACTIVE;
  @Column(name = "created_by", nullable = false)
  private String createdBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  private String note;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getEventNo() { return eventNo; }
  public void setEventNo(String eventNo) { this.eventNo = eventNo; }
  public OwnershipEventType getEventType() { return eventType; }
  public void setEventType(OwnershipEventType eventType) { this.eventType = eventType; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getEquipmentCode() { return equipmentCode; }
  public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
  public EvidenceType getEvidenceType() { return evidenceType; }
  public void setEvidenceType(EvidenceType evidenceType) { this.evidenceType = evidenceType; }
  public String getEvidenceNo() { return evidenceNo; }
  public void setEvidenceNo(String evidenceNo) { this.evidenceNo = evidenceNo; }
  public LocalDate getEffectiveDate() { return effectiveDate; }
  public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
  public String getCounterparty() { return counterparty; }
  public void setCounterparty(String counterparty) { this.counterparty = counterparty; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public Long getConflictWith() { return conflictWith; }
  public void setConflictWith(Long conflictWith) { this.conflictWith = conflictWith; }
  public Long getSupersedesEventId() { return supersedesEventId; }
  public void setSupersedesEventId(Long supersedesEventId) { this.supersedesEventId = supersedesEventId; }
  public EventStatus getStatus() { return status; }
  public void setStatus(EventStatus status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
}
