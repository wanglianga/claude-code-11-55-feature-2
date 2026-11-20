package com.group.carbon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** API 请求体定义。 */
public final class Requests {
  private Requests() {}

  public record LoginReq(@NotBlank(message = "用户名不能为空") String username,
                         @NotBlank(message = "密码不能为空") String password) {}

  /** plantId 为空时取批次工厂；集团采购批次可按行归属不同工厂；equipmentCode 标记共享/租赁设备 */
  public record RowInput(Long plantId,
                         Long productLineId,
                         @NotBlank(message = "能源类型不能为空") String energyType,
                         @NotNull(message = "数量不能为空") BigDecimal quantity,
                         String voucherNo,
                         String equipmentCode) {}

  public record CreateBatchReq(@NotNull(message = "工厂不能为空") Long plantId,
                               @NotBlank(message = "批次类别不能为空") String category,
                               @NotBlank(message = "期间不能为空") String period,
                               String batchNo,
                               @NotEmpty(message = "批次至少包含一行数据") List<RowInput> rows) {}

  public record VoidReq(@NotBlank(message = "作废原因不能为空") String reason) {}

  public record CreateFactorReq(@NotBlank(message = "因子编码不能为空") String code,
                                @NotBlank(message = "因子名称不能为空") String name,
                                @NotBlank(message = "能源类型不能为空") String energyType,
                                @NotBlank(message = "地域不能为空") String region) {}

  public record CreateVersionReq(@NotNull(message = "因子值不能为空") BigDecimal value,
                                 @NotBlank(message = "适用期起不能为空") String validFrom,
                                 String validTo) {}

  public record CreateRunReq(@NotBlank(message = "期间不能为空") String period) {}

  public record CreateReportReq(@NotBlank(message = "报告标题不能为空") String title,
                                @NotBlank(message = "期间不能为空") String period,
                                @NotNull(message = "必须选择核算运行") Long runId) {}

  public record FreezeReq(@NotNull(message = "必须选择核算运行") Long runId, String note) {}

  public record ReasonReq(String reason) {}

  // ---------------- 责任归属 ----------------

  public record CreatePartyReq(@NotBlank(message = "主体编码不能为空") String code,
                               @NotBlank(message = "主体名称不能为空") String name,
                               @NotBlank(message = "主体类型不能为空") String partyType) {}

  /** 不可变责任事件登记；conflictEventId 指向相反法律证据，supersedesEventId 用于追溯更正 */
  public record CreateEventReq(@NotBlank(message = "事件类型不能为空") String eventType,
                               @NotNull(message = "工厂不能为空") Long plantId,
                               String equipmentCode,
                               @NotBlank(message = "证据类别不能为空") String evidenceType,
                               @NotBlank(message = "证据文号不能为空") String evidenceNo,
                               @NotBlank(message = "生效日期不能为空") String effectiveDate,
                               String counterparty,
                               Long targetPartyId,
                               Long conflictEventId,
                               Long supersedesEventId,
                               String control,
                               String note) {}

  /** 责任裁定：对控制权与设备交接不一致的待裁定区间给结论 */
  public record CreateRulingReq(@NotNull(message = "工厂不能为空") Long plantId,
                                String equipmentCode,
                                @NotBlank(message = "裁定区间起不能为空") String periodStart,
                                @NotBlank(message = "裁定区间止不能为空") String periodEnd,
                                @NotBlank(message = "裁定结论不能为空") String decision,
                                @NotBlank(message = "裁定依据文号不能为空") String evidenceNo,
                                String reason,
                                Long supersedesRulingId) {}

  /** 冻结后补入活动/裁定：基于候选运行生成新的比较版本（原范围保留） */
  public record CompareVersionReq(@NotNull(message = "必须选择候选核算运行") Long candidateRunId,
                                  String reason) {}
}
