package com.group.carbon.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 可比性注释：解释未决几天为何没有并入任一集团趋势，
 * 以及年中收购/剥离、共享租赁设备、裁定、排除带来的口径变化。
 */
@Entity
@Table(name = "comparability_notes")
public class ComparabilityNote {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_version_id", nullable = false)
  private Long reportVersionId;
  @Column(name = "plant_id")
  private Long plantId;
  /** PENDING_GAP / ACQUISITION / DIVESTITURE / SHARED_EQUIPMENT / RULING / EXCLUSION */
  @Column(name = "note_type", nullable = false)
  private String noteType;
  @Column(nullable = false)
  private String content;
  @Column(name = "segment_start")
  private LocalDate segmentStart;
  @Column(name = "segment_end")
  private LocalDate segmentEnd;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public String getNoteType() { return noteType; }
  public void setNoteType(String noteType) { this.noteType = noteType; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public LocalDate getSegmentStart() { return segmentStart; }
  public void setSegmentStart(LocalDate segmentStart) { this.segmentStart = segmentStart; }
  public LocalDate getSegmentEnd() { return segmentEnd; }
  public void setSegmentEnd(LocalDate segmentEnd) { this.segmentEnd = segmentEnd; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
