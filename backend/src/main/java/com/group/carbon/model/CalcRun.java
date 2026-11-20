package com.group.carbon.model;

import com.group.carbon.common.Types.RunPurpose;
import com.group.carbon.common.Types.RunStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "calc_runs")
public class CalcRun {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_no", nullable = false, unique = true)
  private String runNo;
  @Column(nullable = false)
  private String period;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunPurpose purpose = RunPurpose.OFFICIAL;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunStatus status = RunStatus.RUNNING;
  @Column(name = "triggered_by", nullable = false)
  private String triggeredBy;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
  @Column(name = "started_at")
  private LocalDateTime startedAt;
  @Column(name = "completed_at")
  private LocalDateTime completedAt;
  @Column
  private String error;
  /** 责任状态推导截止点（运行创建时刻；事件流按此复演，运行期间新增事件不影响本次运行） */
  @Column(name = "ownership_cutoff")
  private LocalDateTime ownershipCutoff;
  /** 本次运行是否存在待裁定的责任片段 */
  @Column(name = "has_pending_ownership", nullable = false)
  private boolean hasPendingOwnership = false;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getRunNo() { return runNo; }
  public void setRunNo(String runNo) { this.runNo = runNo; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public RunPurpose getPurpose() { return purpose; }
  public void setPurpose(RunPurpose purpose) { this.purpose = purpose; }
  public RunStatus getStatus() { return status; }
  public void setStatus(RunStatus status) { this.status = status; }
  public String getTriggeredBy() { return triggeredBy; }
  public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
  public LocalDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
  public String getError() { return error; }
  public void setError(String error) { this.error = error; }
  public LocalDateTime getOwnershipCutoff() { return ownershipCutoff; }
  public void setOwnershipCutoff(LocalDateTime ownershipCutoff) { this.ownershipCutoff = ownershipCutoff; }
  public boolean isHasPendingOwnership() { return hasPendingOwnership; }
  public void setHasPendingOwnership(boolean hasPendingOwnership) { this.hasPendingOwnership = hasPendingOwnership; }
}
