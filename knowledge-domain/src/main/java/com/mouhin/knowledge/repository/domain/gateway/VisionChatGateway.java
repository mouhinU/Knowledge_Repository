package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.VisionChatRequest;

/**
 * 视觉模型对话端口（COLA domain 层）。
 *
 * <p>屏蔽底层多模态模型差异，向解析策略提供「文本指令 + 若干图像 → 纯文本」的单轮识别能力。实现方在基础设施层， 且<b>严禁</b>把图像 base64
 * 内容写入日志或异常消息（AGENTS.md 红线 #4：日志/响应不回显敏感/大体积内容）。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public interface VisionChatGateway {

    /**
     * 单轮多模态识别：文本指令 + 图像 → 模型返回纯文本。
     *
     * @param request 视觉对话请求（提示词 + 图像列表）
     * @return 模型返回的文本内容
     */
    String chatWithImages(VisionChatRequest request);
}
