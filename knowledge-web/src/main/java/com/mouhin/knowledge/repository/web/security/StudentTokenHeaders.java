package com.mouhin.knowledge.repository.web.security;

/**
 * 学生端令牌请求头常量。
 *
 * <p>与 {@code AdminTokenAuthFilter.TOKEN_HEADER}（X-Admin-Token）、{@code
 * PlatformTokenAuthFilter.TOKEN_HEADER} （X-Platform-Token）对齐，统一维护学生端令牌头名，消除此前在 {@code
 * ExamTakingController}（5 处）、 {@code StudentAuthController}（2 处）、{@code
 * StudentWrongAnswerController}（1 处）重复的裸字符串（AGENTS.md 红线 #2）。
 */
public final class StudentTokenHeaders {

    private StudentTokenHeaders() {}

    /** 学生端令牌请求头名。 */
    public static final String TOKEN_HEADER = "X-Student-Token";
}
