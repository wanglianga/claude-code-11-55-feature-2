package com.group.carbon.common;

/** 平台全部业务枚举，集中定义避免散落。 */
public final class Types {
  private Types() {}

  public enum Role { ADMIN, ANALYST, AUDITOR, FINANCE }

  /** 批次状态：已上传 / 已核验 / 已驳回 / 冲突 / 被取代 / 已作废 */
  public enum BatchStatus { UPLOADED, VERIFIED, REJECTED, CONFLICT, SUPERSEDED, VOID }

  public enum RowStatus { ACTIVE, SUPERSEDED, VOID }

  /** 因子版本状态：草稿 / 已批准 / 被替换 / 已停用 */
  public enum FactorStatus { DRAFT, APPROVED, REPLACED, RETIRED }

  public enum RunStatus { RUNNING, COMPLETED, FAILED }

  /** OFFICIAL=正式运行；CANDIDATE=影响评估产生的候选运行 */
  public enum RunPurpose { OFFICIAL, CANDIDATE }

  /** OK=正常；PENDING_FACTOR=缺少适用批准因子，指标待核；PENDING_OWNERSHIP=责任待裁定，不并入集团趋势 */
  public enum ResultStatus { OK, PENDING_FACTOR, PENDING_OWNERSHIP }

  /** RECONCILE_FAILED 即“待核”状态：勾稽不平或存在待核指标 */
  public enum ReportStatus { DRAFT, PENDING_REVIEW, RECONCILE_FAILED, APPROVED, PUBLISHED }

  public enum VersionStatus { FROZEN, PUBLISHED, SUPERSEDED, RESTATED }

  public enum ReconcileStatus { OK, MISMATCH, PENDING_FACTOR, PENDING_OWNERSHIP }

  public enum TriggerType {
    SOURCE_SUPPLEMENT, SOURCE_REVISION, SOURCE_MISSING, FACTOR_REVISION,
    OWNERSHIP_RULING, OWNERSHIP_CORRECTION
  }

  public enum ImpactStatus { OPEN, ACKNOWLEDGED, RESOLVED }

  public enum RestatementStatus { PROPOSED, ACCEPTED, REJECTED, RESOLVED }

  // ---------------- 责任归属（运营控制边界） ----------------

  /** 责任主体类型：集团内法人 / 集团外（原股东、出租方等） */
  public enum PartyType { GROUP, EXTERNAL }

  /**
   * 不可变责任事件类型：
   * ACQUISITION 股权交割（取得法人控制权）/ DIVESTITURE 股权剥离（丧失法人控制权）；
   * HANDOVER 设备实际交接 / METER_STOP 出租方停止计量；
   * LEASE 租赁约定（共享租赁设备的运营控制安排）；
   * RULING 责任裁定 / CORRECTION 追溯更正（指向被更正事件，不删除原事件）。
   */
  public enum OwnershipEventType {
    ACQUISITION, DIVESTITURE, HANDOVER, METER_STOP, LEASE, RULING, CORRECTION
  }

  /** 证据类别：法人控制权证据 / 资产归属 / 租赁约定 / 设备交接凭证 / 停止计量通知 / 裁定 / 更正 */
  public enum EvidenceType { LEGAL, ASSET, LEASE, HANDOVER, METERING, RULING, CORRECTION }

  public enum EventStatus { ACTIVE, RULED }

  /** 裁定结论：纳入集团 / 排除集团 / 仍待裁定 */
  public enum RulingDecision { INCLUDE, EXCLUDE, PENDING }

  public enum RulingStatus { ACTIVE, SUPERSEDED }

  /** 活动归属分类：纳入合并 / 排除（非集团控制）/ 待裁定（控制权与设备交接不一致） */
  public enum AllocationClass { INCLUDED, EXCLUDED, PENDING }

  /** 版本比较口径：初始冻结 / 冻结后补入活动或裁定形成的比较版本（原范围保留） */
  public enum ComparisonBasis { INITIAL, POST_FREEZE_COMPARISON }
}
