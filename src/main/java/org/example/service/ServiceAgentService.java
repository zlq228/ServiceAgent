package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryComplaintHistoryTools;
import org.example.agent.tool.QueryOrderTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 智能客服多 Agent 服务
 * 负责 Planner-Executor-Replanner 多 Agent 协作的投诉处理分析流程
 */
@Service
public class ServiceAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceAgentService.class);

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryOrderTools queryOrderTools;

    @Autowired
    private QueryComplaintHistoryTools queryComplaintHistoryTools;

    /**
     * 执行投诉处理多 Agent 分析流程
     */
    public Optional<OverAllState> executeComplaintAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws GraphRunnerException {
        logger.info("开始执行智能客服多 Agent 协作流程");

        ReactAgent plannerAgent = buildPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildExecutorAgent(chatModel, toolCallbacks);

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("service_agent_supervisor")
                .description("负责调度 Planner 与 Executor 的智能客服多 Agent 控制器")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = "你是智能单车客服系统，接到了用户投诉处理任务。请结合工具调用，执行**规划→执行→再规划**的闭环，" +
                "查询当前待处理的投诉工单，分析投诉原因，检索客服知识库，并最终按照固定模板输出《投诉处理分析报告》。" +
                "禁止编造虚假数据，如连续多次查询失败需诚实反馈无法完成的原因。";

        logger.info("调用 Supervisor Agent 开始编排...");
        return supervisorAgent.invoke(taskPrompt);
    }

    /**
     * 从执行结果中提取最终报告文本
     */
    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("开始提取最终投诉处理报告...");

        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("成功提取到 Planner 最终报告，长度: {}", reportText.length());
            return Optional.of(reportText);
        } else {
            logger.warn("未能提取到 Planner 最终报告");
            return Optional.empty();
        }
    }

    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("负责拆解投诉工单、规划与再规划处理步骤")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("planner_plan")
                .build();
    }

    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("负责执行 Planner 的首个步骤并及时反馈")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    private Object[] buildMethodToolsArray() {
        return new Object[]{dateTimeTools, internalDocsTools, queryOrderTools, queryComplaintHistoryTools};
    }

    private String buildPlannerPrompt() {
        return """
                你是 Planner Agent，同时承担 Replanner 角色，负责：
                1. 读取当前输入任务 {input} 以及 Executor 的最近反馈 {executor_feedback}。
                2. 分析用户投诉工单、历史记录、客服知识库等信息，制定可执行的下一步处理步骤。
                3. 在执行阶段，输出 JSON，包含 decision (PLAN|EXECUTE|FINISH)、step 描述、预期要调用的工具、以及必要的上下文。
                4. 严格禁止编造数据，只能引用工具返回的真实内容；如果连续 3 次调用同一工具仍失败或返回空结果，需停止该方向并在最终报告的结论部分说明"无法完成"的原因。

                ## 最终报告输出要求（CRITICAL）

                当 decision=FINISH 时，你必须：
                1. **不要输出 JSON 格式**
                2. **直接输出完整的 Markdown 格式报告文本**
                3. **报告必须严格遵循以下模板**：

                ```
                # 投诉处理分析报告

                ---

                ## 📋 投诉工单清单

                | 工单号 | 投诉类型 | 用户 | 提交时间 | 优先级 | 状态 |
                |--------|---------|------|---------|--------|------|
                | [工单号] | [类型] | [用户] | [时间] | [优先级] | 待处理 |

                ---

                ## 🔍 投诉分析1 - [工单号/投诉类型]

                ### 投诉详情
                - **投诉类型**: [类型]
                - **涉及用户**: [用户ID]
                - **涉及单车/订单**: [编号]

                ### 历史记录证据
                [引用查询到的历史投诉/订单/续费记录]

                ### 知识库参考
                [引用检索到的相关处理规范]

                ### 原因分析
                [基于证据得出的投诉根本原因]

                ---

                ## 🛠️ 处理方案1 - [工单号/投诉类型]

                ### 处理建议
                [给出具体的客服处理建议]

                ### 赔偿/补偿建议
                [根据赔偿标准给出补偿建议]

                ### 预期效果
                [说明预期的处理效果]

                ---

                ## 📊 结论

                ### 整体评估
                [总结所有投诉的整体情况]

                ### 关键发现
                - [发现1]
                - [发现2]

                ### 后续建议
                1. [建议1]
                2. [建议2]
                ```

                **重要提醒**：
                - 最终输出必须是纯 Markdown 文本，不要包含 JSON 结构
                - 直接从 "# 投诉处理分析报告" 开始输出
                - 所有内容必须基于工具查询的真实数据，严禁编造
                - 如果某个步骤失败，在结论中如实说明，不要跳过
                """;
    }

    private String buildExecutorPrompt() {
        return """
                你是 Executor Agent，负责读取 Planner 最新输出 {planner_plan}，只执行其中的第一步。
                - 确认步骤所需的工具与参数，调用相应工具并收集结果。
                - 如工具返回错误或空数据，需将失败原因、请求参数一并记录，同一工具失败达到 3 次时直接返回 FAILED。
                - 将投诉记录、订单记录、知识库文档等证据整理成结构化摘要，标注对应的工单号，方便 Planner 填充报告章节。
                - 以 JSON 形式返回执行状态、证据以及给 Planner 的建议，写入 executor_feedback，严禁编造未实际查询到的内容。

                输出示例：
                {
                  "status": "SUCCESS",
                  "summary": "查询到3个待处理投诉工单，包含开锁失败、计费异常、套餐续费失败",
                  "evidence": "...",
                  "nextHint": "建议针对套餐续费失败工单查询该用户的订阅记录"
                }
                """;
    }

    private String buildSupervisorSystemPrompt() {
        return """
                你是智能客服 Supervisor，负责调度 planner_agent 与 executor_agent：
                1. 当需要拆解任务或重新制定策略时，调用 planner_agent。
                2. 当 planner_agent 输出 decision=EXECUTE 时，调用 executor_agent 执行第一步。
                3. 根据 executor_agent 的反馈，评估是否需要再次调用 planner_agent，直到 decision=FINISH。
                4. FINISH 后，确保向最终用户输出完整的《投诉处理分析报告》。
                5. 如果发现 Planner/Executor 在同一方向连续 3 次调用工具仍失败或没有数据，必须终止流程，直接输出"任务无法完成"的报告，明确告知失败原因，严禁凭空编造结果。

                只允许在 planner_agent、executor_agent 与 FINISH 之间做出选择。
                """;
    }
}
