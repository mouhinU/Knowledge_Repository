package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 异步文章生成命令执行器单测：薄分派层——锁定 question / permission / callback / sessionId / category 完整透传给 {@link
 * ArticleGenerationSupport#generateArticleAsync}，异常原样冒泡。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("异步文章生成命令执行器 (GenerateArticleAsyncCmdExe)")
class GenerateArticleAsyncCmdExeTest {

    private final ArticleGenerationSupport support = mock(ArticleGenerationSupport.class);
    private final GenerateArticleAsyncCmdExe exe = new GenerateArticleAsyncCmdExe(support);

    @Test
    @DisplayName("全部入参原样透传给 support.generateArticleAsync")
    void delegatesAllArguments() {
        Permission permission = new Permission("u-1", "d-1", "STUDENT", false);
        BlackboardProgressCallback callback = mock(BlackboardProgressCallback.class);

        exe.execute("如何讲解牛顿第三定律？", permission, callback, "sess-a", "物理");

        verify(support)
                .generateArticleAsync(
                        eq("如何讲解牛顿第三定律？"), eq(permission), eq(callback), eq("sess-a"), eq("物理"));
    }

    @Test
    @DisplayName("category / callback 为 null 也原样透传")
    void nullArgumentsPassedThrough() {
        Permission permission = new Permission("u-2", null, null, true);

        exe.execute("q", permission, null, "s2", null);

        verify(support).generateArticleAsync(eq("q"), eq(permission), eq(null), eq("s2"), eq(null));
    }

    @Test
    @DisplayName("support 抛异常 → 原样冒泡，分派层不吞并")
    void supportExceptionPropagates() {
        doThrow(new IllegalArgumentException("question must not be blank"))
                .when(support)
                .generateArticleAsync(any(), any(), any(), any(), any());

        assertThatThrownBy(
                        () ->
                                exe.execute(
                                        " ",
                                        new Permission("u", null, null, false),
                                        null,
                                        "s3",
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question must not be blank");
    }
}
