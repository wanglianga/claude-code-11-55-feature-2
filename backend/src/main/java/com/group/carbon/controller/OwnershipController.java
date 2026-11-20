package com.group.carbon.controller;

import com.group.carbon.common.AuthContext;
import com.group.carbon.common.Types.Role;
import com.group.carbon.dto.Requests.CreateEventReq;
import com.group.carbon.dto.Requests.CreatePartyReq;
import com.group.carbon.dto.Requests.CreateRulingReq;
import com.group.carbon.service.OwnershipService;
import com.group.carbon.service.RunService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 责任归属（运营控制边界）API：
 * 不可变事件登记、责任裁定、责任片段预览、活动归属明细、
 * 集团合并口径汇总（纳入/排除/待裁定/可比性注释）、版本责任口径。
 */
@RestController
@RequestMapping("/api/ownership")
public class OwnershipController {
  private final OwnershipService ownershipService;

  public OwnershipController(OwnershipService ownershipService) {
    this.ownershipService = ownershipService;
  }

  // ---------------- 责任主体 ----------------

  @GetMapping("/parties")
  public Object parties() { return ownershipService.listParties(); }

  @PostMapping("/parties")
  public Object createParty(@Valid @RequestBody CreatePartyReq req) {
    AuthContext.requireRole(Role.ADMIN);
    return ownershipService.createParty(req);
  }

  // ---------------- 不可变事件 ----------------

  @GetMapping("/events")
  public Object events(@RequestParam(required = false) Long plantId) {
    return ownershipService.listEvents(plantId);
  }

  @PostMapping("/events")
  public Object registerEvent(@Valid @RequestBody CreateEventReq req) {
    // 证据登记：分析员/管理员；事件不可变，更正只能再登记 CORRECTION 事件
    AuthContext.requireRole(Role.ANALYST, Role.ADMIN);
    return ownershipService.registerEvent(req, AuthContext.require().username());
  }

  // ---------------- 裁定 ----------------

  @GetMapping("/rulings")
  public Object rulings(@RequestParam(required = false) Long plantId) {
    return ownershipService.listRulings(plantId);
  }

  @PostMapping("/rulings")
  public Object registerRuling(@Valid @RequestBody CreateRulingReq req) {
    // 裁定属于审核/裁决动作：审核员/管理员
    AuthContext.requireRole(Role.AUDITOR, Role.ADMIN);
    return ownershipService.registerRuling(req, AuthContext.require().username());
  }

  // ---------------- 责任片段预览（按当前截止点实时推导） ----------------

  @GetMapping("/segments")
  public Map<String, Object> segments(@RequestParam Long plantId,
                                      @RequestParam String period,
                                      @RequestParam(required = false) String equipmentCode) {
    RunService.validatePeriod(period);
    String equip = equipmentCode == null || equipmentCode.isBlank() ? null : equipmentCode.trim();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plantId", plantId);
    out.put("period", period);
    out.put("equipmentCode", equip);
    out.put("derivedAt", LocalDateTime.now());
    out.put("segments", ownershipService.deriveSegments(plantId, equip, period, LocalDateTime.now()));
    return out;
  }

  // ---------------- 活动归属明细 ----------------

  @GetMapping("/runs/{runId}/allocations")
  public Object allocations(@PathVariable Long runId) {
    return ownershipService.allocationsOfRun(runId);
  }

  // ---------------- 集团合并口径 ----------------

  @GetMapping("/consolidated")
  public Map<String, Object> consolidated(@RequestParam String period) {
    return ownershipService.consolidated(period);
  }

  // ---------------- 版本责任口径 ----------------

  @GetMapping("/versions/{versionId}")
  public Map<String, Object> versionOwnership(@PathVariable Long versionId) {
    return ownershipService.versionOwnership(versionId);
  }
}
