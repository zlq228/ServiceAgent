package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryComplaintHistoryTools;
import org.example.agent.tool.QueryOrderTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryOrderTools queryOrderTools;

    @Autowired
    private QueryComplaintHistoryTools queryComplaintHistoryTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${rag.model}")
    private String modelName;

    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(modelName)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）
     * @param history 历史消息列表
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        StringBuilder systemPromptBuilder = new StringBuilder();
        
        // 基础系统提示
        systemPromptBuilder.append("你是一名专业的智能单车客服助手，可以帮助用户解决以下问题：\n");
        systemPromptBuilder.append("1. 单车开锁/还车问题\n");
        systemPromptBuilder.append("2. 计费争议和退款申请\n");
        systemPromptBuilder.append("3. 套餐自动续费问题（包括续费失败、支付渠道限制等）\n");
        systemPromptBuilder.append("4. 车辆损坏上报\n");
        systemPromptBuilder.append("5. 账户和押金问题\n");
        systemPromptBuilder.append("6. 违规停车申诉\n");
        systemPromptBuilder.append("7. 停放点查询和路线规划\n\n");
        systemPromptBuilder.append("可用工具说明：\n");
        systemPromptBuilder.append("- queryInternalDocs: 检索知识库获取解答\n");
        systemPromptBuilder.append("- getCurrentDateTime: 获取当前时间\n");
        systemPromptBuilder.append("- search_location: 搜索地点位置信息\n");
        systemPromptBuilder.append("- calculate_distance: 计算两地距离\n");
        systemPromptBuilder.append("- plan_route: 规划骑行路线\n\n");
        systemPromptBuilder.append("当用户询问停放点、路线、距离等地理位置问题时，使用地图工具。\n\n");
        
        // 添加历史消息
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }
        
        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");
        
        return systemPromptBuilder.toString();
    }

    /**
     * 动态构建方法工具数组
     */
    public Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, internalDocsTools, queryOrderTools, queryComplaintHistoryTools};
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks() {
        return tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表：本地工具 + MCP 工具
     */
    public void logAvailableTools() {
        logger.info("=== 可用工具列表 ===");

        // 本地工具
        logger.info("本地工具:");
        logger.info("  - getCurrentDateTime (DateTimeTools)");
        logger.info("  - queryInternalDocs (InternalDocsTools)");
        logger.info("  - queryOrder (QueryOrderTools)");
        logger.info("  - queryComplaintHistory (QueryComplaintHistoryTools)");

        // MCP 工具
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        if (toolCallbacks.length > 0) {
            logger.info("MCP 工具:");
            for (ToolCallback toolCallback : toolCallbacks) {
                logger.info("  - {}", toolCallback.getToolDefinition().name());
            }
        } else {
            logger.info("MCP 工具: 无");
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
