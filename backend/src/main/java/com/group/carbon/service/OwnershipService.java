package com.group.carbon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group.carbon.common.BizException;
import com.group.carbon.common.Types.*;
import com.group.carbon.dto.Requests.CreateEventReq;
import com.group.carbon.dto.Requests.CreatePartyReq;
import com.group.carbon.dto.Requests.CreateRulingReq;
import com.group.carbon.model.*;
import com.group.carbon.repo.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 责任归属服务（运营控制边界）。
 *
 * 设计路线：保存不可变的收购、剥离、移交、停计量、租赁、裁定、追溯更正事件，
 * 在核算/冻结/查询时按“报告截止点”复演事件流推导当时的控制状态——
 * 修订理由完整、历史可复演，不按当前股权把历史全部重挂。
 *
 * 关键规则：
 * 1. 法人控制权（LEGAL：收购/剥离）与设备实际交接/计量（HANDOVER/METER_STOP/LEASE）
 *    不一致的时段只能待裁定（PENDING），不并入任一集团趋势；
 * 2. 相反法律证据（conflictWith）互相冲突的区间只能待裁定，不能被多数票覆盖；
 * 3. 裁定（RULING）按区间覆盖推导结论；重新裁定以新记录取代旧记录，旧记录保留；
 * 4. 追溯更正以 CORRECTION 事件指向被更正事件，原事件不删除，推导时被更正事件失效；
 * 5. 活动量按责任片段与活动发生期间的天数交集比例切分。
 */
@Service
public class OwnershipService {
  private final OwnershipEventRepo eventRepo;
  private final OwnershipRulingRepo rulingRepo;
  private final OwnershipPartyRepo partyRepo;
  private final ActivityAllocationRepo allocationRepo;
  private final ReportOwnershipSummaryRepo summaryRepo;
  private final ComparabilityNoteRepo noteRepo;
  private final PlantRepo plantRepo;
  private final CalcRunRepo runRepo;
  private final ReportVersionRepo versionRepo;
  private final ImpactService impactService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public OwnershipService(OwnershipEventRepo eventRepo, OwnershipRulingRepo rulingRepo,
                          OwnershipPartyRepo partyRepo, ActivityAllocationRepo allocationRepo,
                          ReportOwnershipSummaryRepo summaryRepo, ComparabilityNoteRepo noteRepo,
                          PlantRepo plantRepo, CalcRunRepo runRepo, ReportVersionRepo versionRepo,
                          @Lazy ImpactService impactService) {
    this.eventRepo = eventRepo;
    this.rulingRepo = rulingRepo;
    this.partyRepo = partyRepo;
    this.allocationRepo = allocationRepo;
    this.summaryRepo = summaryRepo;
    this.noteRepo = noteRepo;
    this.plantRepo = plantRepo;
    this.runRepo = runRepo;
    this.versionRepo = versionRepo;
    this.impactService = impactService;
  }

  // ============================ 主体 ============================

  @Transactional
  public OwnershipParty createParty(CreatePartyReq req) {
    PartyType type;
    try {
      type = PartyType.valueOf(req.partyType());
    } catch (IllegalArgumentException e) {
      throw BizException.badRequest("主体类型必须为 GROUP / EXTERNAL");
    }
    if (partyRepo.findByCode(req.code()).isPresent()) {
      throw BizException.conflict("主体编码已存在: " + req.code());
    }
    OwnershipParty p = new OwnershipParty();
    p.setCode(req.code());
    p.setName(req.name());
    p.setPartyType(type);
    return partyRepo.save(p);
  }

  public List<OwnershipParty> listParties() { return partyRepo.findAll(); }

  // ============================ 不可变事件 ============================

  @Transactional
  public OwnershipEvent registerEvent(CreateEventReq req, String username) {
    OwnershipEventType type;
    EvidenceType evidence;
    try {
      type = OwnershipEventType.valueOf(req.eventType());
      evidence = EvidenceType.valueOf(req.evidenceType());
    } catch (IllegalArgumentException e) {
      throw BizException.badRequest("事件类型/证据类别不合法: " + req.eventType() + " / " + req.evidenceType());
    }
    Plant plant = plantRepo.findById(req.plantId())
        .orElseThrow(() -> BizException.badRequest("工厂不存在: " + req.plantId()));
    LocalDate date = LocalDate.parse(req.effectiveDate());

    OwnershipEvent event = new OwnershipEvent();
    event.setEventNo(RunService.newNo("EVT"));
    event.setEventType(type);
    event.setPlantId(plant.getId());
    event.setEquipmentCode(blankToNull(req.equipmentCode()));
    event.setEvidenceType(evidence);
    event.setEvidenceNo(req.evidenceNo());
    event.setEffectiveDate(date);
    event.setCounterparty(blankToNull(req.counterparty()));
    event.setCreatedBy(username);
    event.setNote(req.note());
    Map<String, Object> payload = new LinkedHashMap<>();
    if (req.targetPartyId() != null) payload.put("targetPartyId", req.targetPartyId());
    if (type == OwnershipEventType.LEASE) {
      // 租赁约定的运营控制方：GROUP=集团运营控制；OTHER=仍由出租方控制
      payload.put("control", "OTHER".equalsIgnoreCase(req.control()) ? "OTHER" : "GROUP");
    }
    if (!payload.isEmpty()) event.setPayload(writePayload(payload));

    // 追溯更正：指向被更正事件；原事件保留但在推导中失效（标记 RULED）
    if (req.supersedesEventId() != null) {
      if (type != OwnershipEventType.CORRECTION) {
        throw BizException.badRequest("只有 CORRECTION 事件可以指向被更正事件");
      }
      OwnershipEvent original = eventRepo.findById(req.supersedesEventId())
          .orElseThrow(() -> BizException.badRequest("被更正事件不存在: " + req.supersedesEventId()));
      event.setSupersedesEventId(original.getId());
      original.setStatus(EventStatus.RULED);
      eventRepo.save(original);
    }

    // 相反法律证据：显式指向冲突事件，双向标记
    if (req.conflictEventId() != null) {
      OwnershipEvent opposite = eventRepo.findById(req.conflictEventId())
          .orElseThrow(() -> BizException.badRequest("冲突事件不存在: " + req.conflictEventId()));
      event.setConflictWith(opposite.getId());
    }
    eventRepo.save(event);
    if (req.conflictEventId() != null) {
      OwnershipEvent opposite = eventRepo.findById(req.conflictEventId()).orElseThrow();
      if (!Objects.equals(opposite.getConflictWith(), event.getId())) {
        opposite.setConflictWith(event.getId());
        eventRepo.save(opposite);
      }
    }

    // 更正事件若影响已发布报告期间，自动形成影响评估（独立事务，不覆盖已发布版本）
    if (type == OwnershipEventType.CORRECTION) {
      triggerImpact(plant.getId(), date, TriggerType.OWNERSHIP_CORRECTION,
          "追溯更正事件 " + event.getEventNo() + "（证据 " + event.getEvidenceNo() + "）");
    }
    return eventRepo.findById(event.getId()).orElseThrow();
  }

  public List<OwnershipEvent> listEvents(Long plantId) {
    return plantId == null ? eventRepo.findAllByOrderByEffectiveDateAscIdAsc()
        : eventRepo.findByPlantIdOrderByEffectiveDateAscIdAsc(plantId);
  }

  // ============================ 裁定 ============================

  @Transactional
  public OwnershipRuling registerRuling(CreateRulingReq req, String username) {
    RulingDecision decision;
    try {
      decision = RulingDecision.valueOf(req.decision());
    } catch (IllegalArgumentException e) {
      throw BizException.badRequest("裁定结论必须为 INCLUDE / EXCLUDE / PENDING");
    }
    Plant plant = plantRepo.findById(req.plantId())
        .orElseThrow(() -> BizException.badRequest("工厂不存在: " + req.plantId()));
    LocalDate start = LocalDate.parse(req.periodStart());
    LocalDate end = LocalDate.parse(req.periodEnd());
    if (end.isBefore(start)) throw BizException.badRequest("裁定区间止不能早于区间起");

    OwnershipRuling ruling = new OwnershipRuling();
    ruling.setRulingNo(RunService.newNo("RUL"));
    ruling.setPlantId(plant.getId());
    ruling.setEquipmentCode(blankToNull(req.equipmentCode()));
    ruling.setPeriodStart(start);
    ruling.setPeriodEnd(end);
    ruling.setDecision(decision);
    ruling.setEvidenceNo(req.evidenceNo());
    ruling.setReason(req.reason());
    ruling.setCreatedBy(username);
    if (req.supersedesRulingId() != null) {
      OwnershipRuling old = rulingRepo.findById(req.supersedesRulingId())
          .orElseThrow(() -> BizException.badRequest("被取代裁定不存在: " + req.supersedesRulingId()));
      old.setStatus(RulingStatus.SUPERSEDED);
      rulingRepo.save(old);
      ruling.setSupersedesRulingId(old.getId());
    }
    rulingRepo.save(ruling);

    // 裁定同时进入不可变事件流，保证历史复演完整
    OwnershipEvent mirror = new OwnershipEvent();
    mirror.setEventNo(RunService.newNo("EVT"));
    mirror.setEventType(OwnershipEventType.RULING);
    mirror.setPlantId(plant.getId());
    mirror.setEquipmentCode(ruling.getEquipmentCode());
    mirror.setEvidenceType(EvidenceType.RULING);
    mirror.setEvidenceNo(req.evidenceNo());
    mirror.setEffectiveDate(start);
    mirror.setCreatedBy(username);
    mirror.setNote("裁定 " + ruling.getRulingNo() + "（" + decision + "）：" + req.reason());
    mirror.setPayload(writePayload(Map.of(
        "rulingId", ruling.getId(), "decision", decision.name(), "periodEnd", end.toString())));
    eventRepo.save(mirror);

    triggerImpact(plant.getId(), start, TriggerType.OWNERSHIP_RULING,
        "责任裁定 " + ruling.getRulingNo() + "（证据 " + ruling.getEvidenceNo() + "，结论 " + decision + "）");
    return ruling;
  }

  public List<OwnershipRuling> listRulings(Long plantId) {
    return plantId == null ? rulingRepo.findAll()
        : rulingRepo.findByPlantIdOrderByPeriodStartAscIdAsc(plantId);
  }

  /** 影响评估在独立事务中运行：无已发布报告/运行时不产生，且不阻断事件落库。 */
  private void triggerImpact(Long plantId, LocalDate date, TriggerType type, String ref) {
    String period = YearMonth.from(date).toString();
    try {
      impactService.assessInNewTransaction(period, type, ref);
    } catch (Exception ignore) {
      // 派生动作失败不影响事件/裁定本身
    }
  }

  // ============================ 截止点推导 ============================

  /** 责任片段（压缩自逐日推导）。 */
  public record Segment(LocalDate start, LocalDate end, AllocationClass classification,
                        Long partyId, String basis) {}

  private static final class DayState {
    AllocationClass cls = AllocationClass.EXCLUDED;
    Long partyId;
    String basis = "无集团运营控制证据，排除";
  }

  /**
   * 按报告截止点复演事件流，推导某工厂/设备在一个自然月内的逐日控制状态并压缩为片段。
   * cutoff 之后登记的事件/裁定一律不可见——保证历史查询与冻结时刻口径一致。
   */
  public List<Segment> deriveSegments(Long plantId, String equipmentCode, String period, LocalDateTime cutoff) {
    YearMonth ym = YearMonth.parse(period);
    LocalDate monthStart = ym.atDay(1);
    LocalDate monthEnd = ym.atEndOfMonth();
    int days = ym.lengthOfMonth();

    List<OwnershipEvent> events = eventsOf(plantId, equipmentCode).stream()
        .filter(e -> e.getStatus() == EventStatus.ACTIVE)
        .filter(e -> !e.getCreatedAt().isAfter(cutoff))
        .filter(e -> !isSuperseded(e, cutoff))
        .sorted(Comparator.comparing(OwnershipEvent::getEffectiveDate)
            .thenComparing(OwnershipEvent::getId))
        .toList();

    DayState[] dayStates = new DayState[days];
    for (int i = 0; i < days; i++) dayStates[i] = new DayState();

    // 1) 法人控制权时间线（LEGAL：收购/剥离）
    TreeMap<LocalDate, Boolean> legalFlip = new TreeMap<>();
    TreeMap<LocalDate, Long> partyFlip = new TreeMap<>();
    for (OwnershipEvent e : events) {
      if (e.getEvidenceType() != EvidenceType.LEGAL) continue;
      if (e.getEventType() == OwnershipEventType.ACQUISITION) {
        legalFlip.put(e.getEffectiveDate(), Boolean.TRUE);
        Long pid = readPartyId(e);
        if (pid != null) partyFlip.put(e.getEffectiveDate(), pid);
      } else if (e.getEventType() == OwnershipEventType.DIVESTITURE) {
        legalFlip.put(e.getEffectiveDate(), Boolean.FALSE);
      }
    }
    boolean legalGroup = false;
    Long legalParty = null;
    for (int i = 0; i < days; i++) {
      LocalDate d = monthStart.plusDays(i);
      for (Map.Entry<LocalDate, Boolean> en : legalFlip.entrySet()) {
        if (!en.getKey().isAfter(d)) legalGroup = en.getValue();
      }
      for (Map.Entry<LocalDate, Long> en : partyFlip.entrySet()) {
        if (!en.getKey().isAfter(d)) legalParty = en.getValue();
      }
      dayStates[i].cls = legalGroup ? AllocationClass.INCLUDED : AllocationClass.EXCLUDED;
      dayStates[i].partyId = legalParty;
      dayStates[i].basis = legalGroup ? "法人控制权证据：集团承担运营控制" : "无法人控制权证据：集团外主体承担";
    }

    // 2) 设备交接/停计量/租赁时间线（三态）：
    //    LESSOR=租赁约定由出租方运营控制（明确排除）；
    //    TRANSITION=出租方已停止计量但设备尚未交接（只能待裁定）；
    //    GROUP=已交接/租赁约定由集团控制（与法人控制权一致时纳入，不一致时待裁定）。
    TreeMap<LocalDate, String> opFlip = new TreeMap<>();
    for (OwnershipEvent e : events) {
      OwnershipEventType t = e.getEventType();
      if (t == OwnershipEventType.HANDOVER) {
        opFlip.put(e.getEffectiveDate(), "GROUP");
      } else if (t == OwnershipEventType.METER_STOP) {
        opFlip.put(e.getEffectiveDate(), "TRANSITION");
      } else if (t == OwnershipEventType.LEASE) {
        Map<String, Object> payload = readPayload(e);
        String control = String.valueOf(payload.getOrDefault("control", "GROUP"));
        opFlip.put(e.getEffectiveDate(), "OTHER".equalsIgnoreCase(control) ? "LESSOR" : "GROUP");
      }
    }
    if (!opFlip.isEmpty()) {
      // 存在运营事件时，事件首次生效前视为集团外运营（未交接）
      String opState = "LESSOR";
      for (int i = 0; i < days; i++) {
        LocalDate d = monthStart.plusDays(i);
        boolean lg = false;
        for (Map.Entry<LocalDate, Boolean> en : legalFlip.entrySet()) {
          if (!en.getKey().isAfter(d)) lg = en.getValue();
        }
        for (Map.Entry<LocalDate, String> en : opFlip.entrySet()) {
          if (!en.getKey().isAfter(d)) opState = en.getValue();
        }
        if ("LESSOR".equals(opState)) {
          if (!lg) {
            // 法律与运营口径均为集团外：明确排除
            dayStates[i].cls = AllocationClass.EXCLUDED;
            dayStates[i].basis = "租赁约定由出租方承担运营控制且集团尚未取得法人控制权，不纳入合并";
          } else {
            // 股权已交割但租赁设备仍由出租方运营：控制权与设备交接不一致，只能待裁定
            dayStates[i].cls = AllocationClass.PENDING;
            dayStates[i].basis = "股权已交割但租赁设备仍由出租方运营（待裁定）";
          }
        } else if ("TRANSITION".equals(opState)) {
          // 出租方已停止计量、设备尚未完成交接：未决几天只能待裁定
          dayStates[i].cls = AllocationClass.PENDING;
          dayStates[i].basis = "出租方已停止计量但设备交接未完成（待裁定）：法律口径="
              + (lg ? "集团控制" : "集团外");
        } else { // GROUP
          if (!lg) {
            // 设备已交接但股权尚未交割：同样只能待裁定
            dayStates[i].cls = AllocationClass.PENDING;
            dayStates[i].basis = "设备已交接但法人控制权尚未交割（待裁定）";
          }
        }
      }
    }

    // 3) 相反法律证据冲突区间：只能待裁定，不能被多数票覆盖
    for (OwnershipEvent e : events) {
      if (e.getConflictWith() == null) continue;
      OwnershipEvent other = eventRepo.findById(e.getConflictWith()).orElse(null);
      // 对方事件若已被追溯更正/裁定覆盖（RULED），冲突随之解除，不再产生待裁定
      if (other == null || other.getCreatedAt().isAfter(cutoff)) continue;
      if (other.getStatus() != EventStatus.ACTIVE || isSuperseded(other, cutoff)) continue;
      LocalDate from = e.getEffectiveDate().isBefore(other.getEffectiveDate())
          ? e.getEffectiveDate() : other.getEffectiveDate();
      LocalDate to = e.getEffectiveDate().isAfter(other.getEffectiveDate())
          ? e.getEffectiveDate() : other.getEffectiveDate();
      LocalDate segStart = from.isBefore(monthStart) ? monthStart : from;
      LocalDate segEnd = to.isAfter(monthEnd) ? monthEnd : to;
      for (int i = 0; i < days; i++) {
        LocalDate d = monthStart.plusDays(i);
        if (!d.isBefore(segStart) && !d.isAfter(segEnd)) {
          dayStates[i].cls = AllocationClass.PENDING;
          dayStates[i].basis = "相反法律证据冲突（不得多数票覆盖）：证据 " + e.getEvidenceNo()
              + " 与 " + other.getEvidenceNo();
        }
      }
    }

    // 4) 裁定覆盖：按区间给最终结论
    List<OwnershipRuling> rulings = rulingRepo.findByStatusOrderByPeriodStartAscIdAsc(RulingStatus.ACTIVE)
        .stream()
        .filter(r -> r.getPlantId().equals(plantId))
        .filter(r -> Objects.equals(blankToNull(r.getEquipmentCode()), blankToNull(equipmentCode)))
        .filter(r -> !r.getCreatedAt().isAfter(cutoff))
        .toList();
    for (OwnershipRuling r : rulings) {
      LocalDate segStart = r.getPeriodStart().isBefore(monthStart) ? monthStart : r.getPeriodStart();
      LocalDate segEnd = r.getPeriodEnd().isAfter(monthEnd) ? monthEnd : r.getPeriodEnd();
      for (int i = 0; i < days; i++) {
        LocalDate d = monthStart.plusDays(i);
        if (d.isBefore(segStart) || d.isAfter(segEnd)) continue;
        if (r.getDecision() == RulingDecision.INCLUDE) {
          dayStates[i].cls = AllocationClass.INCLUDED;
          dayStates[i].basis = "责任裁定纳入（" + r.getEvidenceNo() + "）";
        } else if (r.getDecision() == RulingDecision.EXCLUDE) {
          dayStates[i].cls = AllocationClass.EXCLUDED;
          dayStates[i].basis = "责任裁定排除（" + r.getEvidenceNo() + "）";
        } else {
          dayStates[i].cls = AllocationClass.PENDING;
          dayStates[i].basis = "裁定维持待裁定（" + r.getEvidenceNo() + "）";
        }
      }
    }

    // 5) 压缩相邻同状态同依据片段
    List<Segment> segments = new ArrayList<>();
    Segment current = null;
    for (int i = 0; i < days; i++) {
      DayState st = dayStates[i];
      LocalDate d = monthStart.plusDays(i);
      if (current == null || current.classification() != st.cls || !current.basis().equals(st.basis)) {
        current = new Segment(d, d, st.cls, st.partyId, st.basis);
        segments.add(current);
      } else {
        current = new Segment(current.start(), d, current.classification(), current.partyId(), current.basis());
        segments.set(segments.size() - 1, current);
      }
    }
    return segments;
  }

  private List<OwnershipEvent> eventsOf(Long plantId, String equipmentCode) {
    return eventRepo.findByPlantIdOrderByEffectiveDateAscIdAsc(plantId).stream()
        .filter(e -> equipmentCode == null ? e.getEquipmentCode() == null
            : (e.getEquipmentCode() == null || equipmentCode.equals(e.getEquipmentCode())))
        .toList();
  }

  private boolean isSuperseded(OwnershipEvent e, LocalDateTime cutoff) {
    return eventRepo.findBySupersedesEventId(e.getId()).stream()
        .anyMatch(c -> c.getStatus() == EventStatus.ACTIVE && !c.getCreatedAt().isAfter(cutoff));
  }

  // ============================ 核算切分 ============================

  /**
   * 核算时把一组来源活动量按责任片段交集切分并落库；
   * 排放按运行绑定因子计算（缺因子或待裁定时为空，仅作参照不进合计）。
   */
  @Transactional
  public List<ActivityAllocation> allocateRun(CalcRun run, List<SourceRow> rows,
                                              List<RunFactorBinding> bindings,
                                              Map<Long, Plant> plants) {
    List<ActivityAllocation> all = new ArrayList<>();
    for (SourceRow row : rows) {
      Plant plant = plants.get(row.getPlantId());
      if (plant == null) throw BizException.badRequest("来源行引用了不存在的工厂: " + row.getPlantId());
      List<Segment> segments = deriveSegments(row.getPlantId(), row.getEquipmentCode(),
          row.getPeriod(), run.getOwnershipCutoff());
      YearMonth ym = YearMonth.parse(row.getPeriod());
      int daysInMonth = ym.lengthOfMonth();
      RunFactorBinding binding = bindings.stream()
          .filter(b -> b.getEnergyType().equals(row.getEnergyType())
              && b.getRegion().equals(plant.getRegion()))
          .findFirst().orElse(null);

      // 按天比例切分；末段吸收舍入差，保证片段之和等于原始活动量
      BigDecimal allocated = BigDecimal.ZERO;
      BigDecimal[] qtys = new BigDecimal[segments.size()];
      for (int i = 0; i < segments.size(); i++) {
        Segment seg = segments.get(i);
        int segDays = (int) (seg.end().toEpochDay() - seg.start().toEpochDay() + 1);
        if (i == segments.size() - 1) {
          qtys[i] = row.getQuantity().subtract(allocated);
        } else {
          qtys[i] = row.getQuantity().multiply(BigDecimal.valueOf(segDays))
              .divide(BigDecimal.valueOf(daysInMonth), 3, RoundingMode.HALF_UP);
          allocated = allocated.add(qtys[i]);
        }
      }
      for (int i = 0; i < segments.size(); i++) {
        Segment seg = segments.get(i);
        int segDays = (int) (seg.end().toEpochDay() - seg.start().toEpochDay() + 1);
        ActivityAllocation a = new ActivityAllocation();
        a.setRunId(run.getId());
        a.setSourceRowId(row.getId());
        a.setPlantId(row.getPlantId());
        a.setProductLineId(row.getProductLineId());
        a.setScope(row.getScope());
        a.setEnergyType(row.getEnergyType());
        a.setPeriod(row.getPeriod());
        a.setSegmentStart(seg.start());
        a.setSegmentEnd(seg.end());
        a.setDaysInMonth(daysInMonth);
        a.setDaysInSegment(segDays);
        a.setActivityQty(qtys[i]);
        a.setUnit(row.getUnit());
        a.setClassification(seg.classification());
        a.setPartyId(seg.partyId());
        a.setBasis(seg.basis());
        if (binding != null) {
          a.setFactorVersionId(binding.getFactorVersionId());
          // 待裁定片段只保存 pro forma 参照值，不进入任何合计
          a.setEmissionTco2e(qtys[i].multiply(binding.getValue())
              .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP));
        }
        all.add(allocationRepo.save(a));
      }
    }
    return all;
  }

  // ============================ 冻结固化 ============================

  /** 冻结报告版本时固化责任口径汇总与可比性注释；已冻结内容之后不再变化。 */
  @Transactional
  public void freezeOwnership(ReportVersion version, CalcRun run) {
    List<ActivityAllocation> allocs = allocationRepo.findByRunIdOrderById(run.getId());
    LocalDateTime asOf = run.getOwnershipCutoff() != null ? run.getOwnershipCutoff() : LocalDateTime.now();

    BigDecimal includedAct = BigDecimal.ZERO, excludedAct = BigDecimal.ZERO, pendingAct = BigDecimal.ZERO;
    BigDecimal excludedE = BigDecimal.ZERO, pendingE = BigDecimal.ZERO;
    Set<Long> pendingSegments = new TreeSet<>();
    for (ActivityAllocation a : allocs) {
      BigDecimal e = a.getEmissionTco2e() == null ? BigDecimal.ZERO : a.getEmissionTco2e();
      switch (a.getClassification()) {
        case INCLUDED -> includedAct = includedAct.add(a.getActivityQty());
        case EXCLUDED -> {
          excludedAct = excludedAct.add(a.getActivityQty());
          excludedE = excludedE.add(e);
        }
        case PENDING -> {
          pendingAct = pendingAct.add(a.getActivityQty());
          pendingE = pendingE.add(e);
          pendingSegments.add(a.getId());
        }
      }
    }

    ReportOwnershipSummary summary = new ReportOwnershipSummary();
    summary.setReportVersionId(version.getId());
    summary.setPeriod(run.getPeriod());
    summary.setIncludedTco2e(version.getTotalTco2e());
    summary.setExcludedTco2e(excludedE);
    summary.setPendingTco2e(pendingE);
    summary.setIncludedActivity(includedAct);
    summary.setExcludedActivity(excludedAct);
    summary.setPendingActivity(pendingAct);
    summary.setPendingSegmentCount(pendingSegments.size());
    summary.setDerivedAsOf(asOf);
    summaryRepo.save(summary);

    version.setIncludedTco2e(version.getTotalTco2e());
    version.setExcludedTco2e(excludedE);
    version.setPendingTco2e(pendingE);
    version.setPendingOwnershipCount(pendingSegments.size());

    writeComparabilityNotes(version, run, allocs, asOf);
  }

  private void writeComparabilityNotes(ReportVersion version, CalcRun run,
                                       List<ActivityAllocation> allocs, LocalDateTime asOf) {
    Map<Long, String> plantNames = plantRepo.findAll().stream()
        .collect(Collectors.toMap(Plant::getId, Plant::getName));
    YearMonth ym = YearMonth.parse(run.getPeriod());

    // 待裁定缺口：解释未决几天为何没有并入任一集团趋势
    allocs.stream().filter(a -> a.getClassification() == AllocationClass.PENDING)
        .collect(Collectors.toMap(
            a -> a.getPlantId() + "|" + a.getSegmentStart() + "|" + a.getSegmentEnd() + "|" + a.getBasis(),
            a -> a, (x, y) -> x))
        .values().stream().sorted(Comparator.comparing(ActivityAllocation::getSegmentStart))
        .forEach(a -> {
          ComparabilityNote n = new ComparabilityNote();
          n.setReportVersionId(version.getId());
          n.setPlantId(a.getPlantId());
          n.setNoteType("PENDING_GAP");
          n.setSegmentStart(a.getSegmentStart());
          n.setSegmentEnd(a.getSegmentEnd());
          n.setContent(plantNames.getOrDefault(a.getPlantId(), "?") + " " + a.getSegmentStart() + "~"
              + a.getSegmentEnd() + " 责任待裁定，相关活动量 " + a.getActivityQty().toPlainString()
              + " " + a.getUnit() + " 未并入任一集团趋势。依据：" + a.getBasis());
          noteRepo.save(n);
        });

    // 事件口径变化：年中收购/剥离/共享租赁/裁定
    for (OwnershipEvent e : eventRepo.findAllByOrderByEffectiveDateAscIdAsc()) {
      if (e.getCreatedAt().isAfter(asOf)) continue;
      if (!YearMonth.from(e.getEffectiveDate()).equals(ym)) continue;
      String type = switch (e.getEventType()) {
        case ACQUISITION -> "ACQUISITION";
        case DIVESTITURE -> "DIVESTITURE";
        case LEASE -> "SHARED_EQUIPMENT";
        case RULING -> "RULING";
        default -> null;
      };
      if (type == null) continue;
      ComparabilityNote n = new ComparabilityNote();
      n.setReportVersionId(version.getId());
      n.setPlantId(e.getPlantId());
      n.setNoteType(type);
      n.setSegmentStart(e.getEffectiveDate());
      n.setContent(plantNames.getOrDefault(e.getPlantId(), "?") + " " + labelOf(type)
          + "（事件 " + e.getEventNo() + "，证据 " + e.getEvidenceNo() + "，生效日 " + e.getEffectiveDate()
          + (e.getEquipmentCode() != null ? "，设备 " + e.getEquipmentCode() : "")
          + "），本期口径与往期不可直接比较");
      noteRepo.save(n);
    }

    // 排除片段注释
    Map<Long, List<ActivityAllocation>> excludedByPlant = allocs.stream()
        .filter(a -> a.getClassification() == AllocationClass.EXCLUDED)
        .collect(Collectors.groupingBy(ActivityAllocation::getPlantId));
    excludedByPlant.forEach((plantId, list) -> {
      LocalDate min = list.stream().map(ActivityAllocation::getSegmentStart).min(LocalDate::compareTo).orElseThrow();
      LocalDate max = list.stream().map(ActivityAllocation::getSegmentEnd).max(LocalDate::compareTo).orElseThrow();
      ComparabilityNote n = new ComparabilityNote();
      n.setReportVersionId(version.getId());
      n.setPlantId(plantId);
      n.setNoteType("EXCLUSION");
      n.setSegmentStart(min);
      n.setSegmentEnd(max);
      n.setContent(plantNames.getOrDefault(plantId, "?") + " " + min + "~" + max
          + " 由集团外主体承担运营控制，相关活动量不纳入合并报表（合计 "
          + list.stream().map(ActivityAllocation::getActivityQty).reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString()
          + " " + list.get(0).getUnit() + "）");
      noteRepo.save(n);
    });
  }

  private String labelOf(String type) {
    return switch (type) {
      case "ACQUISITION" -> "年中收购，自交割日起纳入";
      case "DIVESTITURE" -> "股权剥离，自交割日起排除";
      case "SHARED_EQUIPMENT" -> "共享租赁设备安排";
      case "RULING" -> "责任裁定影响口径";
      default -> type;
    };
  }

  // ============================ 查询 ============================

  public Map<String, Object> versionOwnership(Long versionId) {
    ReportOwnershipSummary summary = summaryRepo.findByReportVersionId(versionId)
        .orElseThrow(() -> BizException.notFound("该版本暂无责任归属汇总（可能由旧口径冻结）: " + versionId));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("summary", summary);
    out.put("notes", noteRepo.findByReportVersionIdOrderById(versionId));
    return out;
  }

  /** 集团合并口径汇总：纳入 / 排除 / 待裁定 + 可比性注释。 */
  public Map<String, Object> consolidated(String period) {
    RunService.validatePeriod(period);
    Map<String, Object> out = new LinkedHashMap<>();
    CalcRun run = runRepo.findFirstByPeriodAndPurposeAndStatusOrderByIdDesc(
        period, RunPurpose.OFFICIAL, RunStatus.COMPLETED).orElse(null);
    if (run == null) {
      out.put("period", period);
      out.put("runId", null);
      out.put("plants", List.of());
      out.put("totals", emptyTotals());
      out.put("notes", List.of());
      return out;
    }
    List<ActivityAllocation> allocs = allocationRepo.findByRunIdOrderById(run.getId());
    Map<Long, String> plantNames = plantRepo.findAll().stream()
        .collect(Collectors.toMap(Plant::getId, Plant::getName));

    Map<Long, BigDecimal[]> agg = new TreeMap<>();
    Map<Long, Long> pendingCount = new TreeMap<>();
    for (ActivityAllocation a : allocs) {
      BigDecimal[] arr = agg.computeIfAbsent(a.getPlantId(), k -> {
        BigDecimal[] x = new BigDecimal[3];
        Arrays.fill(x, BigDecimal.ZERO);
        return x;
      });
      BigDecimal e = a.getEmissionTco2e() == null ? BigDecimal.ZERO : a.getEmissionTco2e();
      int idx = a.getClassification() == AllocationClass.INCLUDED ? 0
          : a.getClassification() == AllocationClass.EXCLUDED ? 1 : 2;
      arr[idx] = arr[idx].add(e);
      if (a.getClassification() == AllocationClass.PENDING) {
        pendingCount.merge(a.getPlantId(), 1L, Long::sum);
      }
    }
    List<Map<String, Object>> plantRows = new ArrayList<>();
    for (Map.Entry<Long, BigDecimal[]> en : agg.entrySet()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("plantId", en.getKey());
      row.put("plantName", plantNames.getOrDefault(en.getKey(), "?"));
      row.put("includedTco2e", en.getValue()[0]);
      row.put("excludedTco2e", en.getValue()[1]);
      row.put("pendingTco2e", en.getValue()[2]);
      row.put("pendingSegmentCount", pendingCount.getOrDefault(en.getKey(), 0L));
      plantRows.add(row);
    }

    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("includedTco2e", sumEmission(allocs, AllocationClass.INCLUDED));
    totals.put("excludedTco2e", sumEmission(allocs, AllocationClass.EXCLUDED));
    totals.put("pendingTco2e", sumEmission(allocs, AllocationClass.PENDING));
    totals.put("pendingSegmentCount", allocs.stream()
        .filter(a -> a.getClassification() == AllocationClass.PENDING).count());
    out.put("period", period);
    out.put("runId", run.getId());
    out.put("runNo", run.getRunNo());
    out.put("cutoff", run.getOwnershipCutoff());
    out.put("hasPendingOwnership", run.isHasPendingOwnership());
    out.put("plants", plantRows);
    out.put("totals", totals);

    List<ReportVersion> published = versionRepo.findPublishedByPeriod(period);
    if (!published.isEmpty()) {
      out.put("publishedVersionId", published.get(0).getId());
      out.put("notes", noteRepo.findByReportVersionIdOrderById(published.get(0).getId()));
    } else {
      out.put("publishedVersionId", null);
      out.put("notes", List.of());
    }
    return out;
  }

  private BigDecimal sumEmission(List<ActivityAllocation> allocs, AllocationClass cls) {
    return allocs.stream().filter(a -> a.getClassification() == cls)
        .map(a -> a.getEmissionTco2e() == null ? BigDecimal.ZERO : a.getEmissionTco2e())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Map<String, Object> emptyTotals() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("includedTco2e", BigDecimal.ZERO);
    m.put("excludedTco2e", BigDecimal.ZERO);
    m.put("pendingTco2e", BigDecimal.ZERO);
    m.put("pendingSegmentCount", 0);
    return m;
  }

  public List<ActivityAllocation> allocationsOfRun(Long runId) {
    return allocationRepo.findByRunIdOrderById(runId);
  }

  // ============================ 杂项 ============================

  private String writePayload(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw BizException.badRequest("事件参数序列化失败: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readPayload(OwnershipEvent e) {
    if (e.getPayload() == null || e.getPayload().isBlank()) return Map.of();
    try {
      return objectMapper.readValue(e.getPayload(), Map.class);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private Long readPartyId(OwnershipEvent e) {
    Object v = readPayload(e).get("targetPartyId");
    return v == null ? null : Long.valueOf(String.valueOf(v));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
