package com.group.carbon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报告版本责任归属汇总：冻结时按当时截止点推导并固化，后续补入活动/裁定不修改本行。 */
@Entity
@Table(name = "report_ownership_summaries")
public class ReportOwnershipSummary {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "report_version_id", nullable = false, unique = true)
  private Long reportVersionId;
  @Column(nullable = false)
  private String period;
  @Column(name = "included_tco2e", nullable = false)
  private BigDecimal includedTco2e = BigDecimal.ZERO;
  @Column(name = "excluded_tco2e", nullable = false)
  private BigDecimal excludedTco2e = BigDecimal.ZERO;
  @Column(name = "pending_tco2e", nullable = false)
  private BigDecimal pendingTco2e = BigDecimal.ZERO;
  @Column(name = "included_activity", nullable = false)
  private BigDecimal includedActivity = BigDecimal.ZERO;
  @Column(name = "excluded_activity", nullable = false)
  private BigDecimal excludedActivity = BigDecimal.ZERO;
  @Column(name = "pending_activity", nullable = false)
  private BigDecimal pendingActivity = BigDecimal.ZERO;
  @Column(name = "pending_segment_count", nullable = false)
  private int pendingSegmentCount;
  /** 推导所用报告截止点：冻结时刻；历史复演即按该时刻之前的事件/裁定重放 */
  @Column(name = "derived_as_of", nullable = false)
  private LocalDateTime derivedAsOf;
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReportVersionId() { return reportVersionId; }
  public void setReportVersionId(Long reportVersionId) { this.reportVersionId = reportVersionId; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public BigDecimal getIncludedTco2e() { return includedTco2e; }
  public void setIncludedTco2e(BigDecimal includedTco2e) { this.includedTco2e = includedTco2e; }
  public BigDecimal getExcludedTco2e() { return excludedTco2e; }
  public void setExcludedTco2e(BigDecimal excludedTco2e) { this.excludedTco2e = excludedTco2e; }
  public BigDecimal getPendingTco2e() { return pendingTco2e; }
  public void setPendingTco2e(BigDecimal pendingTco2e) { this.pendingTco2e = pendingTco2e; }
  public BigDecimal getIncludedActivity() { return includedActivity; }
  public void setIncludedActivity(BigDecimal includedActivity) { this.includedActivity = includedActivity; }
  public BigDecimal getExcludedActivity() { return excludedActivity; }
  public void setExcludedActivity(BigDecimal excludedActivity) { this.excludedActivity = excludedActivity; }
  public BigDecimal getPendingActivity() { return pendingActivity; }
  public void setPendingActivity(BigDecimal pendingActivity) { this.pendingActivity = pendingActivity; }
  public int getPendingSegmentCount() { return pendingSegmentCount; }
  public void setPendingSegmentCount(int pendingSegmentCount) { this.pendingSegmentCount = pendingSegmentCount; }
  public LocalDateTime getDerivedAsOf() { return derivedAsOf; }
  public void setDerivedAsOf(LocalDateTime derivedAsOf) { this.derivedAsOf = derivedAsOf; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
