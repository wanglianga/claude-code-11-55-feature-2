package com.group.carbon.service;

import com.group.carbon.common.BizException;
import com.group.carbon.common.EnergyTypes;
import com.group.carbon.common.Types.*;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核算运行编排：
 * 1. 启动时快照当前已批准因子清单（run_factor_bindings），运行期间因子被替换不影响本运行；
 * 2. 按 工厂地域 + 能源类型 + 适用期间 匹配批准因子，缺因子只把对应指标列为待核，不用集团平均数兜底；
 * 3. 结果与来源行、因子版本建立血缘。
 */
@Service
public class RunService {
  private final CalcRunRepo runRepo;
  private final RunFactorBindingRepo bindingRepo;
  private final EmissionResultRepo resultRepo;
  private final EmissionLineageRepo lineageRepo;
  private final SourceRowRepo rowRepo;
  private final SourceBatchRepo batchRepo;
  private final FactorRepo factorRepo;
  private final FactorVersionRepo versionRepo;
  private final PlantRepo plantRepo;
  private final OwnershipService ownershipService;

  public RunService(CalcRunRepo runRepo, RunFactorBindingRepo bindingRepo,
                    EmissionResultRepo resultRepo, EmissionLineageRepo lineageRepo,
                    SourceRowRepo rowRepo, SourceBatchRepo batchRepo,
                    FactorRepo factorRepo, FactorVersionRepo versionRepo, PlantRepo plantRepo,
                    OwnershipService ownershipService) {
    this.runRepo = runRepo;
    this.bindingRepo = bindingRepo;
    this.resultRepo = resultRepo;
    this.lineageRepo = lineageRepo;
    this.rowRepo = rowRepo;
    this.batchRepo = batchRepo;
    this.factorRepo = factorRepo;
    this.versionRepo = versionRepo;
    this.plantRepo = plantRepo;
    this.ownershipService = ownershipService;
  }

  public static void validatePeriod(String period) {
    if (period == null || !period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
      throw BizException.badRequest("期间格式必须为 YYYY-MM，例如 2025-06");
    }
  }

  /** 创建正式运行：绑定启动时刻的批准因子清单。并发重复运行由数据库唯一索引拦截。 */
  @Transactional
  public CalcRun createOfficialRun(String period, String username) {
    validatePeriod(period);
    CalcRun run = new CalcRun();
    run.setRunNo(newNo("RUN"));
    run.setPeriod(period);
    run.setPurpose(RunPurpose.OFFICIAL);
    run.setStatus(RunStatus.RUNNING);
    run.setTriggeredBy(username);
    run.setStartedAt(LocalDateTime.now());
    // 责任状态推导截止点固定为运行创建时刻：运行期间新增事件/裁定不影响本次运行
    run.setOwnershipCutoff(LocalDateTime.now());
    runRepo.save(run);
    snapshotBindings(run);
    return run;
  }

  /** 候选运行（影响评估用）：同步创建并立即执行完成。 */
  @Transactional
  public CalcRun createCandidateRun(String period, String triggeredBy) {
    validatePeriod(period);
    CalcRun run = new CalcRun();
    run.setRunNo(newNo("CAND"));
    run.setPeriod(period);
    run.setPurpose(RunPurpose.CANDIDATE);
    run.setStatus(RunStatus.RUNNING);
    run.setTriggeredBy(triggeredBy);
    run.setStartedAt(LocalDateTime.now());
    run.setOwnershipCutoff(LocalDateTime.now());
    runRepo.save(run);
    snapshotBindings(run);
    execute(run.getId());
    return runRepo.findById(run.getId()).orElseThrow();
  }

  private void snapshotBindings(CalcRun run) {
    LocalDate periodStart = LocalDate.parse(run.getPeriod() + "-01");
    List<FactorVersion> approved = versionRepo.findByStatus(FactorStatus.APPROVED);
    // 同一因子若存在多个覆盖该期间的批准版本，取版本号最大者
    Map<Long, FactorVersion> byFactor = new LinkedHashMap<>();
    for (FactorVersion v : approved) {
      boolean covers = !v.getValidFrom().isAfter(periodStart)
          && (v.getValidTo() == null || !v.getValidTo().isBefore(periodStart));
      if (!covers) continue;
      FactorVersion prev = byFactor.get(v.getFactorId());
      if (prev == null || v.getVersionNo() > prev.getVersionNo()) byFactor.put(v.getFactorId(), v);
    }
    for (FactorVersion v : byFactor.values()) {
      Factor f = factorRepo.findById(v.getFactorId()).orElseThrow();
      RunFactorBinding b = new RunFactorBinding();
      b.setRunId(run.getId());
      b.setFactorVersionId(v.getId());
      b.setFactorCode(f.getCode());
      b.setRegion(f.getRegion());
      b.setEnergyType(f.getEnergyType());
      b.setValue(v.getValue());
      b.setValidFrom(v.getValidFrom());
      b.setValidTo(v.getValidTo());
      bindingRepo.save(b);
    }
  }

  private record GroupKey(Long plantId, Long productLineId, String energyType) {}
  private record ResultKey(Long plantId, Long productLineId, String energyType, AllocationClass cls) {}

  /**
   * 执行核算：
   * 1. 按运行截止点复演责任事件流，用责任区间交集把每条活动量切分为纳入/排除/待裁定片段；
   * 2. 仅“纳入”片段进入合并核算（因子仍取运行启动时绑定的快照）；
   * 3. 排除/待裁定片段单独成项，排放量不并入集团合计；缺因子只列待核，不用集团平均数兜底；
   * 4. 结果与来源行、因子版本建立血缘。
   */
  @Transactional
  public void execute(Long runId) {
    CalcRun run = runRepo.findById(runId).orElseThrow(() -> BizException.notFound("运行不存在"));
    if (run.getStatus() != RunStatus.RUNNING) return;
    try {
      List<RunFactorBinding> bindings = bindingRepo.findByRunId(runId);
      List<SourceRow> rows = rowRepo.findActiveVerifiedByPeriod(run.getPeriod(), RowStatus.ACTIVE);
      Map<Long, Plant> plants = plantRepo.findAll().stream()
          .collect(Collectors.toMap(Plant::getId, p -> p));

      // 1) 责任区间交集切分（事件流按 run.ownershipCutoff 复演）
      List<ActivityAllocation> allocations = ownershipService.allocateRun(run, rows, bindings, plants);
      run.setHasPendingOwnership(allocations.stream()
          .anyMatch(a -> a.getClassification() == AllocationClass.PENDING));

      // 2) 按 工厂×产品线×能源类型×归属分类 聚合片段
      Map<ResultKey, List<ActivityAllocation>> groups = new LinkedHashMap<>();
      for (ActivityAllocation a : allocations) {
        groups.computeIfAbsent(
            new ResultKey(a.getPlantId(), a.getProductLineId(), a.getEnergyType(), a.getClassification()),
            k -> new ArrayList<>()).add(a);
      }

      for (Map.Entry<ResultKey, List<ActivityAllocation>> e : groups.entrySet()) {
        ResultKey key = e.getKey();
        BigDecimal qty = e.getValue().stream()
            .map(ActivityAllocation::getActivityQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        Plant plant = plants.get(key.plantId());
        if (plant == null) throw BizException.badRequest("来源行引用了不存在的工厂: " + key.plantId());

        RunFactorBinding binding = bindings.stream()
            .filter(b -> b.getEnergyType().equals(key.energyType())
                && b.getRegion().equals(plant.getRegion()))
            .findFirst().orElse(null);

        EmissionResult res = new EmissionResult();
        res.setRunId(runId);
        res.setPlantId(key.plantId());
        res.setProductLineId(key.productLineId());
        res.setScope(EnergyTypes.scopeOf(key.energyType()));
        res.setEnergyType(key.energyType());
        res.setActivityQty(qty);
        res.setUnit(EnergyTypes.unitOf(key.energyType()));
        res.setClassification(key.cls());

        if (key.cls() == AllocationClass.PENDING) {
          // 控制权与设备交接不一致：待裁定，pro forma 排放不并入任何集团趋势
          res.setStatus(ResultStatus.PENDING_OWNERSHIP);
          if (binding != null) {
            res.setFactorVersionId(binding.getFactorVersionId());
            res.setFactorValue(binding.getValue());
          }
        } else if (key.cls() == AllocationClass.EXCLUDED) {
          // 非集团运营控制期间：排除出合并报表，不计算集团口径排放
          res.setStatus(ResultStatus.OK);
        } else if (binding != null) {
          res.setFactorVersionId(binding.getFactorVersionId());
          res.setFactorValue(binding.getValue());
          res.setEmissionTco2e(qty.multiply(binding.getValue())
              .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP));
          res.setStatus(ResultStatus.OK);
        } else {
          // 缺少适用批准因子：指标列为待核，不用集团平均因子兜底
          res.setStatus(ResultStatus.PENDING_FACTOR);
        }
        resultRepo.save(res);

        // 3) 血缘：结果 ← 来源行（按切分后实际占用的活动量）
        Map<Long, BigDecimal> qtyByRow = new LinkedHashMap<>();
        for (ActivityAllocation a : e.getValue()) {
          qtyByRow.merge(a.getSourceRowId(), a.getActivityQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> en : qtyByRow.entrySet()) {
          lineageRepo.save(new EmissionLineage(res.getId(), en.getKey(), en.getValue()));
        }
      }
      run.setStatus(RunStatus.COMPLETED);
      run.setCompletedAt(LocalDateTime.now());
      runRepo.save(run);
    } catch (Exception ex) {
      run.setStatus(RunStatus.FAILED);
      run.setError(ex.getMessage());
      run.setCompletedAt(LocalDateTime.now());
      runRepo.save(run);
    }
  }

  // ---------------- 查询 ----------------

  public List<CalcRun> listRuns() { return runRepo.findAllByOrderByIdDesc(); }

  public CalcRun getRun(Long id) {
    return runRepo.findById(id).orElseThrow(() -> BizException.notFound("运行不存在: " + id));
  }

  public List<RunFactorBinding> bindingsOf(Long runId) { return bindingRepo.findByRunId(runId); }

  public List<EmissionResult> resultsOf(Long runId) { return resultRepo.findByRunIdOrderById(runId); }

  /** 血缘下钻：某条排放结果 ← 哪份来源行（表计/凭证）+ 哪版因子。 */
  public Map<String, Object> lineageOf(Long resultId) {
    EmissionResult res = resultRepo.findById(resultId)
        .orElseThrow(() -> BizException.notFound("排放结果不存在: " + resultId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("result", res);

    Map<String, Object> factorInfo = null;
    if (res.getFactorVersionId() != null) {
      FactorVersion v = versionRepo.findById(res.getFactorVersionId()).orElse(null);
      if (v != null) {
        Factor f = factorRepo.findById(v.getFactorId()).orElse(null);
        factorInfo = new LinkedHashMap<>();
        factorInfo.put("factorCode", f != null ? f.getCode() : null);
        factorInfo.put("factorName", f != null ? f.getName() : null);
        factorInfo.put("region", f != null ? f.getRegion() : null);
        factorInfo.put("versionId", v.getId());
        factorInfo.put("versionNo", v.getVersionNo());
        factorInfo.put("value", v.getValue());
        factorInfo.put("validFrom", v.getValidFrom());
        factorInfo.put("validTo", v.getValidTo());
        factorInfo.put("status", v.getStatus());
        factorInfo.put("approvedBy", v.getApprovedBy());
        factorInfo.put("approvedAt", v.getApprovedAt());
      }
    }
    out.put("factorVersion", factorInfo);

    List<EmissionLineage> edges = lineageRepo.findByResultId(resultId);
    List<Map<String, Object>> sources = new ArrayList<>();
    for (EmissionLineage edge : edges) {
      SourceRow row = rowRepo.findById(edge.getSourceRowId()).orElse(null);
      if (row == null) continue;
      SourceBatch batch = batchRepo.findById(row.getBatchId()).orElse(null);
      Map<String, Object> s = new LinkedHashMap<>();
      s.put("rowId", row.getId());
      s.put("rowNo", row.getRowNo());
      s.put("batchId", row.getBatchId());
      s.put("batchNo", batch != null ? batch.getBatchNo() : null);
      s.put("batchStatus", batch != null ? batch.getStatus() : null);
      s.put("verifiedBy", batch != null ? batch.getVerifiedBy() : null);
      s.put("verifiedAt", batch != null ? batch.getVerifiedAt() : null);
      s.put("uploadedBy", batch != null ? batch.getUploadedBy() : null);
      s.put("voucherNo", row.getVoucherNo());
      s.put("quantity", row.getQuantity());
      s.put("qtyUsed", edge.getQtyUsed());
      s.put("unit", row.getUnit());
      s.put("rowStatus", row.getStatus());
      sources.add(s);
    }
    out.put("sources", sources);
    return out;
  }

  public static String newNo(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
