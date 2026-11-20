package com.group.carbon.model;

import com.group.carbon.common.Types.AllocationClass;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 活动责任归属明细：核算时按责任区间交集切分每条来源活动量。
 * 一行 = 某来源行在某责任片段内的活动量及其纳入/排除/待裁定结论与依据。
 */
@Entity
@Table(name = "activity_allocations")
public class ActivityAllocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "run_id", nullable = false)
  private Long runId;
  @Column(name = "result_id")
  private Long resultId;
  @Column(name = "source_row_id", nullable = false)
  private Long sourceRowId;
  @Column(name = "plant_id", nullable = false)
  private Long plantId;
  @Column(name = "product_line_id")
  private Long productLineId;
  @Column(nullable = false)
  private String scope;
  @Column(name = "energy_type", nullable = false)
  private String energyType;
  @Column(nullable = false)
  private String period;
  @Column(name = "segment_start", nullable = false)
  private LocalDate segmentStart;
  @Column(name = "segment_end", nullable = false)
  private LocalDate segmentEnd;
  @Column(name = "days_in_month", nullable = false)
  private int daysInMonth;
  @Column(name = "days_in_segment", nullable = false)
  private int daysInSegment;
  @Column(name = "activity_qty", nullable = false)
  private BigDecimal activityQty;
  @Column(nullable = false)
  private String unit;
  /** 片段排放；待裁定或缺少因子时为空（ pro forma 参照值不进集团合计） */
  @Column(name = "emission_tco2e")
  private BigDecimal emissionTco2e;
  @Column(name = "factor_version_id")
  private Long factorVersionId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AllocationClass classification;
  @Column(name = "party_id")
  private Long partyId;
  /** 归属依据：命中的事件文号/裁定文号，或待裁定缺口原因 */
  private String basis;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getRunId() { return runId; }
  public void setRunId(Long runId) { this.runId = runId; }
  public Long getResultId() { return resultId; }
  public void setResultId(Long resultId) { this.resultId = resultId; }
  public Long getSourceRowId() { return sourceRowId; }
  public void setSourceRowId(Long sourceRowId) { this.sourceRowId = sourceRowId; }
  public Long getPlantId() { return plantId; }
  public void setPlantId(Long plantId) { this.plantId = plantId; }
  public Long getProductLineId() { return productLineId; }
  public void setProductLineId(Long productLineId) { this.productLineId = productLineId; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public String getEnergyType() { return energyType; }
  public void setEnergyType(String energyType) { this.energyType = energyType; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public LocalDate getSegmentStart() { return segmentStart; }
  public void setSegmentStart(LocalDate segmentStart) { this.segmentStart = segmentStart; }
  public LocalDate getSegmentEnd() { return segmentEnd; }
  public void setSegmentEnd(LocalDate segmentEnd) { this.segmentEnd = segmentEnd; }
  public int getDaysInMonth() { return daysInMonth; }
  public void setDaysInMonth(int daysInMonth) { this.daysInMonth = daysInMonth; }
  public int getDaysInSegment() { return daysInSegment; }
  public void setDaysInSegment(int daysInSegment) { this.daysInSegment = daysInSegment; }
  public BigDecimal getActivityQty() { return activityQty; }
  public void setActivityQty(BigDecimal activityQty) { this.activityQty = activityQty; }
  public String getUnit() { return unit; }
  public void setUnit(String unit) { this.unit = unit; }
  public BigDecimal getEmissionTco2e() { return emissionTco2e; }
  public void setEmissionTco2e(BigDecimal emissionTco2e) { this.emissionTco2e = emissionTco2e; }
  public Long getFactorVersionId() { return factorVersionId; }
  public void setFactorVersionId(Long factorVersionId) { this.factorVersionId = factorVersionId; }
  public AllocationClass getClassification() { return classification; }
  public void setClassification(AllocationClass classification) { this.classification = classification; }
  public Long getPartyId() { return partyId; }
  public void setPartyId(Long partyId) { this.partyId = partyId; }
  public String getBasis() { return basis; }
  public void setBasis(String basis) { this.basis = basis; }
}
