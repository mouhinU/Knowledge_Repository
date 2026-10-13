package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantPlanLimitGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台端计费数据导出控制器（adapter 层，P6-T4）。
 *
 * <p>挂载 {@code /api/platform/billing/export}，提供 CSV / JSON 格式的计费数据导出。 仅当 {@code
 * knowledge.platform.enabled=true} 才注册本控制器。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/api/platform/billing")
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
@Slf4j
public class PlatformBillingController {

    private static final String PARAM_FROM = "from";
    private static final String PARAM_TO = "to";
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_FORMAT = "format";
    private static final String FORMAT_CSV = "csv";
    private static final String FORMAT_JSON = "json";
    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TenantUsageRecorder tenantUsageRecorder;
    private final TenantPlanLimitGateway tenantPlanLimitGateway;
    private final TenantGateway tenantGateway;

    public PlatformBillingController(
            TenantUsageRecorder tenantUsageRecorder,
            TenantPlanLimitGateway tenantPlanLimitGateway,
            TenantGateway tenantGateway) {
        this.tenantUsageRecorder = tenantUsageRecorder;
        this.tenantPlanLimitGateway = tenantPlanLimitGateway;
        this.tenantGateway = tenantGateway;
    }

    private static List<YearMonth> enumerateMonths(YearMonth from, YearMonth to) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth current = from;
        while (!current.isAfter(to)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        return months;
    }

    private static YearMonth parseYearMonth(String value, YearMonth fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return YearMonth.parse(value.trim(), YEAR_MONTH_FMT);
    }

    /**
     * 简易 JSON 序列化（避免引入额外依赖，v0.1 字段均为字符串或长整型，无嵌套结构）。
     */
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        int idx = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (idx > 0) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof Number) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
            idx++;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 导出计费数据。
     *
     * <p>按租户、周期、指标三维聚合，输出 CSV 或 JSON。
     *
     * @param from   起始月份（yyyy-MM，可选，默认当月）
     * @param to     结束月份（yyyy-MM，可选，默认当月）
     * @param tenant 租户键（可选，不传则遍历全部租户）
     * @param format 输出格式（csv / json，默认 csv）
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(value = PARAM_FROM, required = false) String from,
            @RequestParam(value = PARAM_TO, required = false) String to,
            @RequestParam(value = PARAM_TENANT, required = false) String tenant,
            @RequestParam(value = PARAM_FORMAT, required = false, defaultValue = FORMAT_CSV)
            String format,
            HttpServletResponse response)
            throws IOException {

        YearMonth fromYm = parseYearMonth(from, YearMonth.now());
        YearMonth toYm = parseYearMonth(to, YearMonth.now());
        if (toYm.isBefore(fromYm)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "to must not be before from");
            return;
        }

        List<String> tenantKeys = resolveTenantKeys(tenant);
        List<YearMonth> months = enumerateMonths(fromYm, toYm);
        List<BillingRow> rows = collectRows(tenantKeys, months);

        if (FORMAT_JSON.equalsIgnoreCase(format)) {
            writeJson(rows, response);
        } else {
            writeCsv(rows, response);
        }
    }

    private List<String> resolveTenantKeys(String tenant) {
        if (tenant != null && !tenant.isBlank()) {
            return List.of(tenant.trim());
        }
        return tenantGateway.listAll().stream().map(Tenant::getTenantKey).toList();
    }

    private List<BillingRow> collectRows(List<String> tenantKeys, List<YearMonth> months) {
        List<BillingRow> rows = new ArrayList<>();
        for (String tenantKey : tenantKeys) {
            for (YearMonth ym : months) {
                LocalDate period = ym.atDay(1);
                for (UsageMetric metric : UsageMetric.values()) {
                    long usage = tenantUsageRecorder.getCurrentUsage(tenantKey, metric, period);
                    long quota = resolveQuota(tenantKey, metric);
                    rows.add(
                            new BillingRow(
                                    tenantKey,
                                    ym.format(YEAR_MONTH_FMT),
                                    metric.code(),
                                    quota,
                                    usage));
                }
            }
        }
        return rows;
    }

    private long resolveQuota(String tenantKey, UsageMetric metric) {
        return tenantPlanLimitGateway
                .findByTenantKeyAndMetric(tenantKey, metric)
                .map(TenantPlanLimit::quota)
                .orElse(-1L);
    }

    private void writeCsv(List<BillingRow> rows, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=billing-export.csv");
        PrintWriter writer = response.getWriter();
        writer.println("tenant_key,period,metric,quota,current_usage");
        for (BillingRow row : rows) {
            writer.printf(
                    "%s,%s,%s,%d,%d%n",
                    row.tenantKey, row.period, row.metric, row.quota, row.currentUsage);
        }
        writer.flush();
    }

    private void writeJson(List<BillingRow> rows, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=billing-export.json");
        PrintWriter writer = response.getWriter();
        writer.println("[");
        for (int i = 0; i < rows.size(); i++) {
            BillingRow row = rows.get(i);
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("tenant_key", row.tenantKey);
            obj.put("period", row.period);
            obj.put("metric", row.metric);
            obj.put("quota", row.quota);
            obj.put("current_usage", row.currentUsage);
            String json = mapToJson(obj);
            writer.print(json);
            if (i < rows.size() - 1) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
        writer.println("]");
        writer.flush();
    }

    /**
     * 计费导出行数据（内部 record，不越过 adapter 层）。
     */
    private record BillingRow(
            String tenantKey, String period, String metric, long quota, long currentUsage) {
    }
}
