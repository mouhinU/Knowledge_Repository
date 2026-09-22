package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;

/**
 * 黑板进度回调接口
 *
 * <p>Agent 在执行过程中通过此接口推送进度事件， 前端通过 SSE 实时接收并展示。
 *
 * @author mouhinU
 * @date 2026-09-13
 */
public interface BlackboardProgressCallback {

    /**
     * 推送进度事件
     *
     * @param event 进度事件
     */
    void onProgress(BlackboardProgressEvent event);
}
