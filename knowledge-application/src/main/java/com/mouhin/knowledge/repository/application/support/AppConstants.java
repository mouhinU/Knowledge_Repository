package com.mouhin.knowledge.repository.application.support;

/**
 * 应用层共享常量。
 *
 * <p>收敛 app 层多处重复内联的字面量（AGENTS.md 红线 #2），当前承载业务统一时区标识； 后续新增跨 Executor 复用的常量在此归类维护。
 */
public final class AppConstants {

    private AppConstants() {}

    /** 业务统一时区标识（落库时间戳、历史列表时间等一律以东八区为准）。 */
    public static final String ZONE_ID_SHANGHAI = "Asia/Shanghai";
}
