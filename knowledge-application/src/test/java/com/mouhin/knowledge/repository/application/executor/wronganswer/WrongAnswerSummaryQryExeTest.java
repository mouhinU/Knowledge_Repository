package com.mouhin.knowledge.repository.application.executor.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * AI 错题总结查询执行器单测：锁定无错题短路（不调大模型）、提示词组装（条数 / 题型分布统计 / null 字段'（无）'占位）、 AI 空回复与调用异常的兜底文案。列表用例通过 mock
 * {@link WrongAnswerListQryExe} 隔离。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("AI 错题总结查询执行器 (WrongAnswerSummaryQryExe)")
class WrongAnswerSummaryQryExeTest {

    private final WrongAnswerListQryExe listQryExe = mock(WrongAnswerListQryExe.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final WrongAnswerSummaryQryExe exe =
            new WrongAnswerSummaryQryExe(listQryExe, chatModel);

    private WrongAnswerVO item(String type, Integer maxScore, Integer effScore) {
        WrongAnswerVO vo = new WrongAnswerVO();
        vo.setTopic("函数专题");
        vo.setQuestionType(type);
        vo.setQuestionContent("已知 f(x)=x²，求 f(2)");
        vo.setMaxScore(maxScore);
        vo.setEffectiveScore(effScore);
        vo.setStudentAnswer("3");
        vo.setCorrectAnswer("4");
        vo.setAiFeedback("计算失误");
        return vo;
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    @Test
    @DisplayName("错题列表为空 → '暂无错题数据'，不触碰大模型")
    void emptyListShortCircuits() {
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of());

        String result = exe.execute(1L, null, null);

        assertThat(result).contains("暂无错题数据");
        verify(chatModel, org.mockito.Mockito.never()).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("成功总结：prompt 含总条数与题型分布统计，AI 文本原样返回")
    void buildsPromptAndReturnsAiText() {
        when(listQryExe.execute(eq(1L), any(), any()))
                .thenReturn(
                        List.of(
                                item("SINGLE_CHOICE", 10, 0),
                                item("SHORT_ANSWER", 12, 5),
                                item("SINGLE_CHOICE", 10, 3)));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("整体计算能力需加强"));

        String result = exe.execute(1L, null, null);

        assertThat(result).isEqualTo("整体计算能力需加强");
        ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(cap.capture());
        List<ChatMessage> messages = cap.getValue().messages();
        UserMessage user =
                messages.stream()
                        .filter(UserMessage.class::isInstance)
                        .map(UserMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(user.singleText()).contains("共 3 题");
        assertThat(user.singleText()).contains("SINGLE_CHOICE：2题");
        assertThat(user.singleText()).contains("SHORT_ANSWER：1题");
    }

    @Test
    @DisplayName("null 字段以'（无）'占位、超长题干截断加省略号")
    void nullFieldsPlaceholder() {
        WrongAnswerVO vo = item("FILL_BLANK", null, null);
        vo.setStudentAnswer(null);
        vo.setCorrectAnswer(null);
        vo.setQuestionContent("长".repeat(300));
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of(vo));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("ok"));

        exe.execute(2L, null, null);

        ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(cap.capture());
        UserMessage user =
                cap.getValue().messages().stream()
                        .filter(UserMessage.class::isInstance)
                        .map(UserMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertThat(user.singleText()).contains("学生答案：（无）");
        assertThat(user.singleText()).contains("正确答案：（无）");
        assertThat(user.singleText()).contains("...");
    }

    @Test
    @DisplayName("AI 返回空白文本 → 兜底'AI 返回为空，请稍后重试。'")
    void blankAiResponseFallback() {
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of(item("T", 10, 0)));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("   "));

        assertThat(exe.execute(3L, null, null)).contains("AI 返回为空");
    }

    @Test
    @DisplayName("大模型调用抛异常 → 吞并并回'AI 总结生成失败：<原因>'")
    void chatFailureFallback() {
        when(listQryExe.execute(any(), any(), any())).thenReturn(List.of(item("T", 10, 0)));
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

        assertThat(exe.execute(4L, null, null)).contains("AI 总结生成失败").contains("timeout");
    }
}
