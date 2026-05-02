package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 投诉历史记录查询工具
 * 用于查询用户的历史投诉记录、订单记录、退款记录等
 */
@Component
public class QueryComplaintHistoryTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryComplaintHistoryTools.class);

    public static final String TOOL_GET_AVAILABLE_QUERY_TYPES = "getAvailableQueryTypes";
    public static final String TOOL_QUERY_COMPLAINT_HISTORY = "queryComplaintHistory";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${complaint-history.mock-enabled:true}")
    private boolean mockEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("✅ QueryComplaintHistoryTools 初始化成功, Mock模式: {}", mockEnabled);
    }

    /**
     * 获取可查询的记录类型列表
     */
    @Tool(description = "Get all available query types for complaint history. " +
            "Call this tool first to understand what types of records can be queried. " +
            "Returns a list of query types with their names and descriptions.")
    public String getAvailableQueryTypes() {
        logger.info("获取可用的查询类型列表");
        try {
            List<QueryTypeInfo> types = new ArrayList<>();

            QueryTypeInfo complaints = new QueryTypeInfo();
            complaints.setTypeName("complaint-records");
            complaints.setDescription("用户历史投诉记录，包含投诉类型、处理结果、处理时间等");
            complaints.setExampleKeywords(List.of("开锁失败", "计费异常", "续费失败", "车辆损坏"));
            types.add(complaints);

            QueryTypeInfo orders = new QueryTypeInfo();
            orders.setTypeName("order-records");
            orders.setDescription("用户骑行订单记录，包含骑行时长、费用、起止时间、单车编号等");
            orders.setExampleKeywords(List.of("订单号", "骑行时长", "扣费金额", "单车编号"));
            types.add(orders);

            QueryTypeInfo refunds = new QueryTypeInfo();
            refunds.setTypeName("refund-records");
            refunds.setDescription("用户退款记录，包含退款金额、退款原因、退款状态、到账时间等");
            refunds.setExampleKeywords(List.of("退款", "押金", "多扣费", "赔偿"));
            types.add(refunds);

            QueryTypeInfo subscriptions = new QueryTypeInfo();
            subscriptions.setTypeName("subscription-records");
            subscriptions.setDescription("用户套餐订阅记录，包含套餐类型、购买时间、续费记录、续费失败原因等");
            subscriptions.setExampleKeywords(List.of("月骑卡", "自动续费", "续费失败", "套餐到期"));
            types.add(subscriptions);

            QueryTypesOutput output = new QueryTypesOutput();
            output.setSuccess(true);
            output.setTypes(types);
            output.setMessage(String.format("共有 %d 种可查询的记录类型", types.size()));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("获取查询类型列表失败", e);
            return "{\"success\":false,\"message\":\"获取查询类型列表失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 查询用户投诉历史记录
     *
     * @param userId    用户ID，如 U-138****8888
     * @param queryType 查询类型：complaint-records / order-records / refund-records / subscription-records
     * @param keyword   关键词过滤，如"续费失败"、"计费异常"
     * @param limit     返回条数，默认10，最大50
     */
    @Tool(description = "Query complaint history, order records, refund records or subscription records for a user. " +
            "Available queryType values: " +
            "1) 'complaint-records' - historical complaint records; " +
            "2) 'order-records' - riding order records with billing details; " +
            "3) 'refund-records' - refund records; " +
            "4) 'subscription-records' - subscription and auto-renewal records. " +
            "userId (required), queryType (required), keyword (optional), limit (optional, default 10).")
    public String queryComplaintHistory(
            @ToolParam(description = "用户ID，如 U-138****8888 或工单中的 userId") String userId,
            @ToolParam(description = "查询类型：complaint-records / order-records / refund-records / subscription-records") String queryType,
            @ToolParam(description = "关键词过滤，如：续费失败、计费异常，为空则返回全部") String keyword,
            @ToolParam(description = "返回条数，默认10，最大50") Integer limit) {

        int actualLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        String safeKeyword = keyword == null ? "" : keyword;
        String safeType = queryType == null ? "complaint-records" : queryType.toLowerCase();

        logger.info("查询用户历史记录 - userId: {}, type: {}, keyword: {}", userId, safeType, safeKeyword);

        try {
            List<HistoryRecord> records = buildMockRecords(userId, safeType, safeKeyword, actualLimit);

            HistoryQueryOutput output = new HistoryQueryOutput();
            output.setSuccess(true);
            output.setUserId(userId);
            output.setQueryType(safeType);
            output.setKeyword(safeKeyword);
            output.setRecords(records);
            output.setTotal(records.size());
            output.setMessage(records.isEmpty()
                    ? "未找到匹配的记录"
                    : String.format("成功查询到 %d 条记录", records.size()));

            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.info("历史记录查询完成: 找到 {} 条记录", records.size());
            return jsonResult;

        } catch (Exception e) {
            logger.error("查询历史记录失败", e);
            return String.format("{\"success\":false,\"message\":\"查询失败: %s\"}", e.getMessage());
        }
    }

    private List<HistoryRecord> buildMockRecords(String userId, String queryType, String keyword, int limit) {
        List<HistoryRecord> records = new ArrayList<>();
        Instant now = Instant.now();

        switch (queryType) {
            case "complaint-records" -> records.addAll(buildComplaintRecords(now, userId, keyword));
            case "order-records" -> records.addAll(buildOrderRecords(now, userId, keyword));
            case "refund-records" -> records.addAll(buildRefundRecords(now, userId, keyword));
            case "subscription-records" -> records.addAll(buildSubscriptionRecords(now, userId, keyword));
            default -> records.addAll(buildComplaintRecords(now, userId, keyword));
        }

        if (records.size() > limit) {
            records = records.subList(0, limit);
        }
        return records;
    }

    private List<HistoryRecord> buildComplaintRecords(Instant now, String userId, String keyword) {
        List<HistoryRecord> records = new ArrayList<>();

        HistoryRecord r1 = new HistoryRecord();
        r1.setRecordId("COMP-20260310-001");
        r1.setType("投诉记录");
        r1.setTitle("开锁失败投诉");
        r1.setContent("用户反映单车BK-SH-003211无法开锁，经排查为设备蓝牙模块故障，已安排维修并补偿1张30分钟骑行券");
        r1.setStatus("已解决");
        r1.setCreatedAt(FORMATTER.format(now.minus(17, ChronoUnit.DAYS)));
        r1.setResolvedAt(FORMATTER.format(now.minus(17, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS)));
        records.add(r1);

        HistoryRecord r2 = new HistoryRecord();
        r2.setRecordId("COMP-20260201-003");
        r2.setType("投诉记录");
        r2.setTitle("计费异常投诉");
        r2.setContent("用户反映骑行20分钟被扣费8元，经核实为还车时GPS定位偏差导致计费延迟，已退还差额3.5元");
        r2.setStatus("已解决");
        r2.setCreatedAt(FORMATTER.format(now.minus(54, ChronoUnit.DAYS)));
        r2.setResolvedAt(FORMATTER.format(now.minus(54, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)));
        records.add(r2);

        return records;
    }

    private List<HistoryRecord> buildOrderRecords(Instant now, String userId, String keyword) {
        List<HistoryRecord> records = new ArrayList<>();

        // 续费失败后的3次原价骑行订单（对应工单WO-20260327-003）
        for (int i = 0; i < 3; i++) {
            HistoryRecord r = new HistoryRecord();
            r.setRecordId("ORD-20260325-" + String.format("%05d", 1001 + i));
            r.setType("骑行订单");
            r.setTitle("骑行记录 - 套餐到期后原价计费");
            r.setContent(String.format(
                    "骑行时长: 28分钟, 实际扣费: 1.5元, 套餐状态: 已到期(续费失败), " +
                    "若套餐有效应扣费: 0元, 差额损失: 1.5元, 单车: BK-SH-00%d",
                    4500 + i));
            r.setStatus("已完成");
            r.setCreatedAt(FORMATTER.format(now.minus(2 - i, ChronoUnit.DAYS)));
            records.add(r);
        }

        // 套餐有效期内的正常订单
        HistoryRecord normal = new HistoryRecord();
        normal.setRecordId("ORD-20260320-00888");
        normal.setType("骑行订单");
        normal.setTitle("骑行记录 - 套餐内免费骑行");
        normal.setContent("骑行时长: 22分钟, 实际扣费: 0元(月骑卡免费), 单车: BK-SH-004100");
        normal.setStatus("已完成");
        normal.setCreatedAt(FORMATTER.format(now.minus(7, ChronoUnit.DAYS)));
        records.add(normal);

        return records;
    }

    private List<HistoryRecord> buildRefundRecords(Instant now, String userId, String keyword) {
        List<HistoryRecord> records = new ArrayList<>();

        HistoryRecord r1 = new HistoryRecord();
        r1.setRecordId("REF-20260201-001");
        r1.setType("退款记录");
        r1.setTitle("计费异常退款");
        r1.setContent("退款金额: 3.5元, 退款原因: GPS定位偏差导致计费延迟, 退款方式: 原路退回支付宝, 到账时间: 1个工作日");
        r1.setStatus("已到账");
        r1.setCreatedAt(FORMATTER.format(now.minus(54, ChronoUnit.DAYS)));
        r1.setResolvedAt(FORMATTER.format(now.minus(53, ChronoUnit.DAYS)));
        records.add(r1);

        return records;
    }

    private List<HistoryRecord> buildSubscriptionRecords(Instant now, String userId, String keyword) {
        List<HistoryRecord> records = new ArrayList<>();

        // 第1次续费 - 成功
        HistoryRecord sub1 = new HistoryRecord();
        sub1.setRecordId("SUB-20260101-001");
        sub1.setType("套餐续费记录");
        sub1.setTitle("月骑卡自动续费 - 第1次 - 成功");
        sub1.setContent("套餐: 月骑卡(15元/月), 续费方式: 自动续费, 支付方式: 微信支付(绑定工商银行储蓄卡尾号6688), " +
                "扣款金额: 15元, 续费结果: 成功, 有效期: 2026-01-01 至 2026-01-31");
        sub1.setStatus("续费成功");
        sub1.setCreatedAt(FORMATTER.format(now.minus(85, ChronoUnit.DAYS)));
        records.add(sub1);

        // 第2次续费 - 成功
        HistoryRecord sub2 = new HistoryRecord();
        sub2.setRecordId("SUB-20260201-001");
        sub2.setType("套餐续费记录");
        sub2.setTitle("月骑卡自动续费 - 第2次 - 成功");
        sub2.setContent("套餐: 月骑卡(15元/月), 续费方式: 自动续费, 支付方式: 微信支付(绑定工商银行储蓄卡尾号6688), " +
                "扣款金额: 15元, 续费结果: 成功, 有效期: 2026-02-01 至 2026-02-28");
        sub2.setStatus("续费成功");
        sub2.setCreatedAt(FORMATTER.format(now.minus(54, ChronoUnit.DAYS)));
        records.add(sub2);

        // 第3次续费 - 失败（银行卡单月代扣次数超限）
        HistoryRecord sub3 = new HistoryRecord();
        sub3.setRecordId("SUB-20260301-001");
        sub3.setType("套餐续费记录");
        sub3.setTitle("月骑卡自动续费 - 第3次 - 失败");
        sub3.setContent("套餐: 月骑卡(15元/月), 续费方式: 自动续费, 支付方式: 微信支付(绑定工商银行储蓄卡尾号6688), " +
                "扣款金额: 15元, 续费结果: 失败, 失败原因: 银行卡单月自动代扣次数超限(该卡本月已代扣2次，达到上限), " +
                "系统重试: 已重试1次仍失败, 通知发送: 【待核实】App推送发送状态未知, 短信发送失败(手机号未验证), " +
                "套餐状态: 已到期(2026-03-01起不再享有免费骑行权益)");
        sub3.setStatus("续费失败");
        sub3.setCreatedAt(FORMATTER.format(now.minus(26, ChronoUnit.DAYS)));
        records.add(sub3);

        return records;
    }

    // ==================== 数据模型 ====================

    @Data
    public static class HistoryRecord {
        @JsonProperty("record_id")
        private String recordId;

        @JsonProperty("type")
        private String type;

        @JsonProperty("title")
        private String title;

        @JsonProperty("content")
        private String content;

        @JsonProperty("status")
        private String status;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("resolved_at")
        private String resolvedAt;
    }

    @Data
    public static class HistoryQueryOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("query_type")
        private String queryType;

        @JsonProperty("keyword")
        private String keyword;

        @JsonProperty("records")
        private List<HistoryRecord> records;

        @JsonProperty("total")
        private int total;

        @JsonProperty("message")
        private String message;
    }

    @Data
    public static class QueryTypeInfo {
        @JsonProperty("type_name")
        private String typeName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("example_keywords")
        private List<String> exampleKeywords;
    }

    @Data
    public static class QueryTypesOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("types")
        private List<QueryTypeInfo> types;

        @JsonProperty("message")
        private String message;
    }
}
