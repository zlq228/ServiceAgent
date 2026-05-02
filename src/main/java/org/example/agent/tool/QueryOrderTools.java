package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 投诉工单查询工具
 * 用于查询当前待处理的用户投诉工单
 */
@Component
public class QueryOrderTools {

    private static final Logger logger = LoggerFactory.getLogger(QueryOrderTools.class);

    public static final String TOOL_QUERY_PENDING_COMPLAINTS = "queryPendingComplaints";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @Value("${complaint.mock-enabled:true}")
    private boolean mockEnabled;

    @jakarta.annotation.PostConstruct
    public void init() {
        logger.info("✅ QueryOrderTools 初始化成功, Mock模式: {}", mockEnabled);
    }

    /**
     * 查询当前待处理的用户投诉工单
     * 该工具从工单系统检索所有当前待处理的投诉工单，包括工单号、用户信息、投诉类型、状态等
     */
    @Tool(description = "Query pending complaint tickets from the customer service system. " +
            "This tool retrieves all currently pending complaint tickets including ticket ID, user info, " +
            "complaint type, bike ID, location, and status. " +
            "Use this tool when you need to check what complaints are currently pending, " +
            "investigate complaint conditions, or analyze complaint patterns.")
    public String queryPendingComplaints() {
        logger.info("开始查询待处理投诉工单, Mock模式: {}", mockEnabled);

        try {
            List<ComplaintTicket> tickets = buildMockTickets();
            logger.info("返回 {} 个待处理投诉工单", tickets.size());

            ComplaintQueryOutput output = new ComplaintQueryOutput();
            output.setSuccess(true);
            output.setTickets(tickets);
            output.setMessage(String.format("成功检索到 %d 个待处理投诉工单", tickets.size()));

            String jsonResult = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.info("投诉工单查询完成: 找到 {} 个工单", tickets.size());
            return jsonResult;

        } catch (Exception e) {
            logger.error("查询投诉工单失败", e);
            return String.format("{\"success\":false,\"message\":\"查询失败: %s\"}", e.getMessage());
        }
    }

    /**
     * 构建 Mock 投诉工单数据
     * 覆盖单车客服常见投诉场景
     */
    private List<ComplaintTicket> buildMockTickets() {
        List<ComplaintTicket> tickets = new ArrayList<>();
        Instant now = Instant.now();

        // 工单1: 开锁失败
        ComplaintTicket t1 = new ComplaintTicket();
        t1.setTicketId("WO-20260327-001");
        t1.setUserId("U-138****8888");
        t1.setComplaintType("开锁失败");
        t1.setBikeId("BK-SH-004521");
        t1.setLocation("上海市徐汇区漕溪路88号");
        t1.setDescription("扫码后App一直转圈，超过2分钟无法开锁，已尝试重启App无效");
        t1.setStatus("待处理");
        t1.setCreatedAt(FORMATTER.format(now.minus(35, ChronoUnit.MINUTES)));
        t1.setPriority("高");
        tickets.add(t1);

        // 工单2: 计费异常（多扣费）
        ComplaintTicket t2 = new ComplaintTicket();
        t2.setTicketId("WO-20260327-002");
        t2.setUserId("U-139****9999");
        t2.setComplaintType("计费异常");
        t2.setBikeId("BK-BJ-009832");
        t2.setOrderId("ORD-20260326-88821");
        t2.setLocation("北京市朝阳区三里屯");
        t2.setDescription("骑行仅15分钟，实际扣费12.5元，远超正常收费标准，怀疑计费系统异常");
        t2.setStatus("待处理");
        t2.setCreatedAt(FORMATTER.format(now.minus(20, ChronoUnit.MINUTES)));
        t2.setPriority("高");
        tickets.add(t2);

        // 工单3: 套餐自动续费失败
        ComplaintTicket t3 = new ComplaintTicket();
        t3.setTicketId("WO-20260327-003");
        t3.setUserId("U-136****6666");
        t3.setComplaintType("套餐续费失败");
        t3.setOrderId("SUB-20260301-55512");
        t3.setLocation("N/A");
        t3.setDescription("购买了月骑卡自动续费，前两次续费正常，第三次续费一直失败，" +
                "但系统未发送任何通知，导致我以为套餐仍有效，按原价骑行了3次，共多扣费4.5元");
        t3.setStatus("待处理");
        t3.setCreatedAt(FORMATTER.format(now.minus(50, ChronoUnit.MINUTES)));
        t3.setPriority("高");
        tickets.add(t3);

        return tickets;
    }

    // ==================== 数据模型 ====================

    @Data
    public static class ComplaintTicket {
        @JsonProperty("ticket_id")
        private String ticketId;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("complaint_type")
        private String complaintType;

        @JsonProperty("bike_id")
        private String bikeId;

        @JsonProperty("order_id")
        private String orderId;

        @JsonProperty("location")
        private String location;

        @JsonProperty("description")
        private String description;

        @JsonProperty("status")
        private String status;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("priority")
        private String priority;
    }

    @Data
    public static class ComplaintQueryOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("tickets")
        private List<ComplaintTicket> tickets;

        @JsonProperty("message")
        private String message;
    }
}
