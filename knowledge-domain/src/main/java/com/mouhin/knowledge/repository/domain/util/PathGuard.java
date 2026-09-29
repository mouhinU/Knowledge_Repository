package com.mouhin.knowledge.repository.domain.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 文件系统出口的路径归一化与白名单校验工具（P0-T4 加固）。
 *
 * <p>收敛"用户可控输入 → {@code Path}"的三类越权面：
 *
 * <ol>
 *   <li>{@link #sanitizeExtension(String)}：从上传文件名截出的扩展名只允许 {@code .[A-Za-z0-9]{1,10}}， 其他（含 {@code
 *       /}{@code \}{@code ..}{@code NUL}）一律丢弃为空串，杜绝把 {@code foo.pdf/../../etc/passwd} 之类的载荷拼进存储路径。
 *   <li>{@link #resolveWithin(Path, Path)}：任何 {@code root.resolve(userInput)} 之后必须 normalize 并 用
 *       {@code startsWith(root)} 白名单校验，防止 {@code ../} 目录穿越；{@code root} 需为绝对路径且已 normalize。
 *   <li>{@link #assertWithinRoots(Path, Path...)}：读取 DB 中 {@code storage_path} 等回环字段时的最后一道防线，
 *       允许多个合法根（例如 asset 根 + 文档存储根），越界抛 {@link IOException}。
 * </ol>
 *
 * <p>放在 domain 层以便 app / infrastructure 共用同一口径，避免各处正则漂移。零外部依赖，仅用 JDK NIO，符合"领域层保持纯净"红线。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public final class PathGuard {

    /** 允许的扩展名形态：点号 + 1~10 位字母数字；其他一律视为不安全。 */
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^\\.[A-Za-z0-9]{1,10}$");

    private PathGuard() {}

    /**
     * 从原始上传文件名截出白名单扩展名。
     *
     * @param originalName 客户端提交的原始文件名，可能为 null
     * @return 合法扩展名（含前导点，如 {@code .pdf}）；不合法时返回空串
     */
    public static String sanitizeExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot);
        return SAFE_EXTENSION.matcher(ext).matches() ? ext : "";
    }

    /**
     * 在 {@code root} 下解析相对段并做 {@code startsWith} 白名单校验。
     *
     * @param root 已 {@code toAbsolutePath().normalize()} 的目录根
     * @param relativeSegment 用户可控的相对段（如拼接后的 storedName）
     * @return 归一化后的目标绝对路径
     * @throws IOException 相对段穿越 root 之外，或参数不合法
     */
    public static Path resolveWithin(Path root, String relativeSegment) throws IOException {
        if (root == null) {
            throw new IOException("path root must not be null");
        }
        if (relativeSegment == null || relativeSegment.isBlank()) {
            throw new IOException("relative segment must not be blank");
        }
        Path absRoot = root.toAbsolutePath().normalize();
        Path candidate = absRoot.resolve(relativeSegment).normalize();
        if (!candidate.startsWith(absRoot)) {
            throw new IOException(
                    "path traversal detected: " + relativeSegment + " escapes root " + absRoot);
        }
        return candidate;
    }

    /**
     * 校验 {@code target}（通常是 DB {@code storage_path} 读回）落在允许的根目录之一内。
     *
     * <p>拒绝绝对路径穿越与符号链接逃逸前的字面路径校验；{@code target} 会先 {@code toAbsolutePath().normalize()}。
     *
     * @param target 待校验的目标路径
     * @param allowedRoots 一个或多个已 normalize 的合法根目录
     * @return 归一化后的 {@code target}
     * @throws IOException 目标不在任何一个 root 之内
     */
    public static Path assertWithinRoots(Path target, Path... allowedRoots) throws IOException {
        if (target == null) {
            throw new IOException("target path must not be null");
        }
        if (allowedRoots == null || allowedRoots.length == 0) {
            throw new IOException("allowed roots must not be empty");
        }
        Path abs = target.toAbsolutePath().normalize();
        for (Path root : allowedRoots) {
            if (root == null) {
                continue;
            }
            Path absRoot = root.toAbsolutePath().normalize();
            if (abs.startsWith(absRoot)) {
                return abs;
            }
        }
        throw new IOException("path outside allowed roots: " + abs);
    }
}
