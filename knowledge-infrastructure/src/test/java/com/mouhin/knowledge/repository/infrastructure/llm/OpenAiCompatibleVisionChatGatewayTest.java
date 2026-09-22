package com.mouhin.knowledge.repository.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.domain.model.valueobject.VisionChatRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OpenAiCompatibleVisionChatGateway} 集成测试（Phase B）。
 *
 * <p>用 JDK 内置 {@link HttpServer} 起一个假 OpenAI 兼容端点（避免引入 WireMock 新依赖），断言：① 请求体为多模态 content 数组并含
 * data-URI；② Authorization 头按密钥有无条件携带；③ 从 choices[0].message.content 解析文本；④ 非 2xx 抛异常且不回显 base64。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("B 视觉网关")
class OpenAiCompatibleVisionChatGatewayTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody =
            new AtomicReference<>("{\"choices\":[{\"message\":{\"content\":\"hello ocr\"}}]}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] out = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private OpenAiCompatibleVisionChatGateway gateway(String apiKey) {
        return new OpenAiCompatibleVisionChatGateway(
                baseUrl,
                apiKey,
                "paddleocr-vl",
                0.0,
                4096,
                Duration.ofSeconds(10),
                HttpClient.newHttpClient());
    }

    private VisionChatRequest oneImageRequest() {
        return new VisionChatRequest(
                "请转录", List.of(new VisionChatRequest.ImagePayload("YQ==", "image/png")));
    }

    @Test
    @DisplayName("多模态请求体 + Bearer 头 + 解析 choices.content")
    void sendsMultimodalAndParsesContent() {
        String text = gateway("secret-key").chatWithImages(oneImageRequest());
        assertThat(text).isEqualTo("hello ocr");
        assertThat(lastBody.get())
                .contains("\"model\":\"paddleocr-vl\"")
                .contains("\"type\":\"image_url\"")
                .contains("data:image/png;base64,YQ==");
        assertThat(lastAuth.get()).isEqualTo("Bearer secret-key");
    }

    @Test
    @DisplayName("空密钥：不下发 Authorization 头")
    void omitsAuthWhenNoKey() {
        gateway("").chatWithImages(oneImageRequest());
        assertThat(lastAuth.get()).isNull();
    }

    @Test
    @DisplayName("非 2xx 抛异常，消息含状态码但不含 base64 内容")
    void throwsOnHttpErrorWithoutLeakingBase64() {
        responseStatus.set(500);
        responseBody.set("{\"error\":{\"message\":\"upstream boom\"}}");
        assertThatThrownBy(() -> gateway("k").chatWithImages(oneImageRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining("YQ==");
    }
}
