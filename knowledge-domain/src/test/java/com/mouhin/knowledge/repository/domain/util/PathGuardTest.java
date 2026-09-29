package com.mouhin.knowledge.repository.domain.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PathGuard} 单元测试：锁定扩展名白名单、路径穿越防护、多根白名单三类不变式。
 *
 * <p>P0-T4 加固回归用例：任何 {@code ..}/{@code /}/{@code \\}/NUL 等注入串都必须被拒或被降级为空扩展名。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("PathGuard 文件出口路径白名单 (P0-T4)")
class PathGuardTest {

    @Nested
    @DisplayName("sanitizeExtension：扩展名白名单")
    class SanitizeExtension {

        @Test
        @DisplayName("合法扩展名原样返回")
        void keepsSafeExtensions() {
            assertThat(PathGuard.sanitizeExtension("foo.pdf")).isEqualTo(".pdf");
            assertThat(PathGuard.sanitizeExtension("foo.DOCX")).isEqualTo(".DOCX");
            assertThat(PathGuard.sanitizeExtension("archive.tar.gz")).isEqualTo(".gz");
            assertThat(PathGuard.sanitizeExtension("a.b123456789")).isEqualTo(".b123456789");
        }

        @Test
        @DisplayName("null / 无点 / 末尾点：返回空串")
        void returnsEmptyOnMissingExtension() {
            assertThat(PathGuard.sanitizeExtension(null)).isEmpty();
            assertThat(PathGuard.sanitizeExtension("README")).isEmpty();
            assertThat(PathGuard.sanitizeExtension("foo.")).isEmpty();
        }

        @Test
        @DisplayName("穿越段与非法字符：/ \\\\ NUL 空格 或 超长 → 一律降级为空")
        void rejectsTraversalAndControlChars() {
            // "/" 在文件名尾部：substring 后含路径段，不匹配 [A-Za-z0-9]+
            assertThat(PathGuard.sanitizeExtension("foo.pdf/../x")).isEmpty();
            // "\\" 同理
            assertThat(PathGuard.sanitizeExtension("foo.pdf\\x")).isEmpty();
            // NUL 不匹配
            assertThat(PathGuard.sanitizeExtension("foo.\0")).isEmpty();
            assertThat(PathGuard.sanitizeExtension("foo.\0txt")).isEmpty();
            // 空格不匹配
            assertThat(PathGuard.sanitizeExtension("foo.p df")).isEmpty();
            // 超长（>10）不匹配
            assertThat(PathGuard.sanitizeExtension("foo." + "a".repeat(200))).isEmpty();
        }

        @Test
        @DisplayName("不变式：sanitize 结果永远以点开头且仅含字母数字，最长 11")
        void invariantNeverCarriesSeparators() {
            String[] inputs = {
                null,
                "",
                "noext",
                "a.b",
                "foo.pdf",
                "evil/../../x",
                "foo\\bar\\baz",
                "foo.\0",
                "foo.p df",
                "a." + "x".repeat(50),
                "multi.dot.name.tar.gz",
            };
            for (String in : inputs) {
                String ext = PathGuard.sanitizeExtension(in);
                assertThat(ext)
                        .as("input=%s 结果=%s 必须以 '.' 起头 + 只含字母数字", in, ext)
                        .matches("(^$)|^\\.[A-Za-z0-9]{1,10}$");
                assertThat(ext.length()).as("扩展名长度 ≤ 11").isLessThanOrEqualTo(11);
            }
        }
    }

    @Nested
    @DisplayName("resolveWithin：normalize + startsWith 白名单")
    class ResolveWithin {

        @Test
        @DisplayName("合法 storedName 归一化到 root 下")
        void resolvesWithinRoot(@TempDir Path root) throws IOException {
            Path absRoot = root.toAbsolutePath().normalize();
            Path result = PathGuard.resolveWithin(root, "abc-123.pdf");
            assertThat(result.startsWith(absRoot)).as("结果 %s 必须落在 %s 之内", result, absRoot).isTrue();
            assertThat(result.getFileName().toString()).isEqualTo("abc-123.pdf");
        }

        @Test
        @DisplayName("'..' 相对段穿越被拒")
        void rejectsDotDotTraversal(@TempDir Path root) {
            assertThatThrownBy(() -> PathGuard.resolveWithin(root, "../evil.pdf"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("path traversal");
        }

        @Test
        @DisplayName("绝对路径段穿越被拒")
        void rejectsAbsolutePathTraversal(@TempDir Path root) {
            assertThatThrownBy(() -> PathGuard.resolveWithin(root, "/etc/passwd"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("path traversal");
        }

        @Test
        @DisplayName("空 / null 参数被拒")
        void rejectsNullArgs(@TempDir Path root) {
            assertThatThrownBy(() -> PathGuard.resolveWithin(root, ""))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> PathGuard.resolveWithin(root, null))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> PathGuard.resolveWithin(null, "x"))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("assertWithinRoots：多根白名单（DB 回读兜底）")
    class AssertWithinRoots {

        @Test
        @DisplayName("落在任一允许 root 内通过")
        void acceptsAnyAllowedRoot(@TempDir Path rootA, @TempDir Path rootB) throws IOException {
            Path inside = rootB.resolve("nested/img.png");
            Path normalized = PathGuard.assertWithinRoots(inside, rootA, rootB);
            assertThat(normalized.startsWith(rootB.toAbsolutePath().normalize())).isTrue();
        }

        @Test
        @DisplayName("所有允许 root 之外被拒")
        void rejectsOutsideAllRoots(@TempDir Path rootA, @TempDir Path outside) {
            assertThatThrownBy(() -> PathGuard.assertWithinRoots(outside.resolve("x.bin"), rootA))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("outside allowed roots");
        }
    }
}
