package com.mouhin.knowledge.repository.application.executor.wronganswer;

import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerStatsVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 错题分页查询执行器（app 层用例）
 * <p>复用核心列表查询，按页切片并附带统计概览。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class WrongAnswerPageQryExe {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final WrongAnswerListQryExe wrongAnswerListQryExe;

    public WrongAnswerPageQryExe(WrongAnswerListQryExe wrongAnswerListQryExe) {
        this.wrongAnswerListQryExe = wrongAnswerListQryExe;
    }

    public WrongAnswerPageVO execute(Long studentId, String topic, String questionType, int page, int size) {
        List<WrongAnswerVO> all = wrongAnswerListQryExe.execute(studentId, topic, questionType);
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<WrongAnswerVO> records = new ArrayList<>(all.subList(from, to));

        WrongAnswerPageVO vo = new WrongAnswerPageVO();
        vo.setRecords(records);
        vo.setTotal(all.size());
        vo.setPage(safePage);
        vo.setSize(safeSize);
        vo.setStats(buildStats(all));
        return vo;
    }

    /**
     * 基于全量错题构建统计概览（错题总数 / 涉及主题 / 涉及考生 / 最多错题题型 / 平均得分率）
     */
    private WrongAnswerStatsVO buildStats(List<WrongAnswerVO> all) {
        WrongAnswerStatsVO stats = new WrongAnswerStatsVO();
        stats.setTotalCount(all.size());
        if (all.isEmpty()) {
            stats.setTopicCount(0L);
            stats.setStudentCount(0L);
            stats.setTopType(null);
            stats.setAvgScoreRate(0.0);
            return stats;
        }

        long topicCount = all.stream()
                .map(WrongAnswerVO::getTopic)
                .filter(Objects::nonNull)
                .distinct().count();
        long studentCount = all.stream()
                .map(WrongAnswerVO::getStudentId)
                .filter(Objects::nonNull)
                .distinct().count();

        Map<String, Long> typeCounts = new HashMap<>();
        double rateSum = 0;
        int rateCount = 0;
        for (WrongAnswerVO item : all) {
            String type = item.getQuestionType() != null ? item.getQuestionType() : "UNKNOWN";
            typeCounts.merge(type, 1L, Long::sum);
            int maxScore = item.getMaxScore() != null ? item.getMaxScore() : 0;
            int effScore = item.getEffectiveScore() != null ? item.getEffectiveScore() : 0;
            if (maxScore > 0) {
                rateSum += (double) effScore / maxScore;
                rateCount++;
            }
        }
        String topType = typeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        double avgScoreRate = rateCount > 0 ? rateSum / rateCount : 0;

        stats.setTopicCount(topicCount);
        stats.setStudentCount(studentCount);
        stats.setTopType(topType);
        stats.setAvgScoreRate(Math.round(avgScoreRate * 10000.0) / 10000.0);
        return stats;
    }
}
