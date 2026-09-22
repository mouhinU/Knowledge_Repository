package com.mouhin.knowledge.repository.application.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 标准答案与评分标准解析器回归测试。
 *
 * <p>冻结「答案标记行内值为空、答案正文写在后续未标记行」这类计算 / 解答题的答案键解析行为 （如试卷校对阶段 {@code 计算与解决问题} 题型答案不渲染的历史缺陷），并顺带覆盖行内答案、
 * 解析、评分标准的既有口径，防止后续改动回归。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@DisplayName("标准答案与评分标准解析器")
class AnswerKeyParserTest {

    @Test
    @DisplayName("行内值非空的答案键：答案/解析/评分标准各自归位（回归保护）")
    void inlineAnswerKey() {
        String md =
                "# 一年级数学期末参考答案\n\n"
                        + "## 一、选择题\n\n"
                        + "**1.（4分）答案：B**  \n"
                        + "解析：6+2=8。  \n"
                        + "评分标准：选B得4分；错选不得分。\n";

        AnswerKeyParser.QuestionKey key = AnswerKeyParser.parse(md).get(1);

        assertThat(key).isNotNull();
        assertThat(key.answer()).isEqualTo("B");
        assertThat(key.analysis()).contains("6+2=8");
        assertThat(key.scoringCriteria()).contains("选B得4分");
    }

    @Test
    @DisplayName("冒号两侧带空格的标记行：所有格量词加固后仍精确剥离首尾空白（ReDoS 回归保护）")
    void colonPaddedWithWhitespaceStillIsolatesBody() {
        String md = "**7.（3分）答案 ：  B  **  \n" + "解析 ：  先算括号里的加法。  \n";

        AnswerKeyParser.QuestionKey key = AnswerKeyParser.parse(md).get(1);

        assertThat(key).isNotNull();
        assertThat(key.answer()).as("冒号两侧空格不得污染捕获值").isEqualTo("B");
        assertThat(key.analysis()).contains("先算括号里的加法");
    }

    @Test
    @DisplayName("空的行内「答案：」+后续多行正文 → 答案正文跨行收集（Q21 计算题）")
    void multilineAnswerAfterEmptyInlineMarker() {
        String md =
                "## 四、计算与解决问题（共2题，共20分）\n\n"
                        + "**21.（10分）答案：**  \n"
                        + "8+2=10　　10-4=6　　9+3=12  \n"
                        + "7+3+2=12　　10-5-3=2  \n"
                        + "评分标准：每个得数1分，共10分。\n";

        Map<Integer, AnswerKeyParser.QuestionKey> parsed = AnswerKeyParser.parse(md);

        // 文档中仅第 21 题一个题号 → 全局题序 seq=1
        AnswerKeyParser.QuestionKey key = parsed.get(1);
        assertThat(key).isNotNull();
        assertThat(key.answer())
                .as("空「答案：」后的未标记正文应被收集为答案")
                .contains("8+2=10")
                .contains("10-4=6")
                .contains("7+3+2=12");
        assertThat(key.scoringCriteria()).as("评分标准仍应单独归位，不被并入答案").contains("每个得数1分");
    }

    @Test
    @DisplayName("应用题：列式与答语跨行归入答案，评分标准独立")
    void wordProblemAnswerAndCriteria() {
        String md =
                "**22.（10分）答案：**  \n"
                        + "列式：10-3+5=12（支）  \n"
                        + "答：现在盒子里有12支铅笔。  \n"
                        + "评分标准：列式正确得6分；计算结果正确得3分；答语完整得1分。\n";

        AnswerKeyParser.QuestionKey key = AnswerKeyParser.parse(md).get(1);

        assertThat(key).isNotNull();
        assertThat(key.answer()).contains("列式：10-3+5=12").contains("答：现在盒子里有12支铅笔");
        assertThat(key.scoringCriteria()).contains("列式正确得6分");
    }

    @Test
    @DisplayName("答案独立成行的「答案：」标记同样切换收集区")
    void answerMarkerOnOwnLineCollectsFollowingText() {
        String md = "**5.（5分）**  \n" + "答案：  \n" + "光合作用  \n" + "解析：绿色植物利用光能合成有机物。\n";

        AnswerKeyParser.QuestionKey key = AnswerKeyParser.parse(md).get(1);

        assertThat(key).isNotNull();
        assertThat(key.answer()).contains("光合作用");
        assertThat(key.analysis()).contains("绿色植物");
    }

    @Test
    @DisplayName("题号行内值为空且无「答案：」标记：后续（1）（2）未标记正文应归入答案（语文第29题连词成句）")
    void emptyInlineHeaderWithoutAnswerMarkerCollectsFollowingBody() {
        String md =
                "## 四、句子题（每题7分，共21分）\n\n"
                        + "**27.（7分）** 示例：树林是小鸟的家。  \n"
                        + "评分标准：句式正确3分。\n\n"
                        + "**28.（7分）** 示例：我在教室里读书。  \n"
                        + "评分标准：用上\"在\"2分。\n\n"
                        + "**29.（7分）**  \n"
                        + "（1）我们是祖国的花朵。  \n"
                        + "（2）影子跟着我。  \n"
                        + "评分标准：每小题3.5分；连词成句正确、句意通顺3分。\n";

        Map<Integer, AnswerKeyParser.QuestionKey> parsed = AnswerKeyParser.parse(md);

        AnswerKeyParser.QuestionKey key = parsed.get(3);
        assertThat(key).isNotNull();
        assertThat(key.answer())
                .as("题号行内值为空时，紧随其后的未标记正文应被收集为答案，而非丢失")
                .contains("我们是祖国的花朵")
                .contains("影子跟着我");
        assertThat(key.scoringCriteria()).as("评分标准仍应单独归位，不被并入答案").contains("连词成句正确");
        // 同行带答案的 27/28 不受影响
        assertThat(parsed.get(1).answer()).contains("树林是小鸟的家");
        assertThat(parsed.get(2).answer()).contains("我在教室里读书");
    }

    @Test
    @DisplayName("大题标题不得串入上一题评分标准（非题号标题关闭收集区）")
    void sectionHeadingDoesNotLeakIntoPreviousCriteria() {
        String md =
                "**29.（7分）**  \n"
                        + "（1）我们是祖国的花朵。  \n"
                        + "评分标准：每小题3.5分，共7分。  \n\n"
                        + "## 五、看图写话（共11分）  \n\n"
                        + "**30.（11分）** 示例：早晨，太阳升起来了。  \n"
                        + "评分标准：内容符合图意得4分。\n";

        Map<Integer, AnswerKeyParser.QuestionKey> parsed = AnswerKeyParser.parse(md);

        AnswerKeyParser.QuestionKey q29 = parsed.get(1);
        assertThat(q29).isNotNull();
        assertThat(q29.scoringCriteria())
                .as("上一题评分标准只应含自身细则，不得并入下一大题标题")
                .contains("每小题3.5分")
                .doesNotContain("看图写话");
        // 下一大题的题仍正常解析
        AnswerKeyParser.QuestionKey q30 = parsed.get(2);
        assertThat(q30).isNotNull();
        assertThat(q30.answer()).contains("早晨，太阳升起来了");
        assertThat(q30.scoringCriteria()).contains("内容符合图意");
    }

    @Test
    @DisplayName("大题区间标题（第1~10题 / 第32题）不得被当作一道新题产生幻影题序")
    void rangeSectionHeadingDoesNotCreatePhantomQuestion() {
        // 复现语文卷缺陷：三个大题区间标题 + 32 道行内题号
        // 若区间标题被误识别为新题，最后一题会漂到 seq=33；修复后严格 32 项、末题落在 seq=32
        StringBuilder md = new StringBuilder();
        md.append("## 一、单选题（第1~2题，共4分）\n\n");
        md.append("**1.（2分）A**  \n\n");
        md.append("**2.（2分）B**  \n\n");
        md.append("## 二、判断题（第3~4题，共4分）\n\n");
        md.append("**3.（2分）正确。**  \n\n");
        md.append("**4.（2分）错误。**  \n\n");
        md.append("## 三、填空题（第5~6题，共6分）\n\n");
        md.append("**5.（3分）蓝天；白云**  \n\n");
        md.append("**6.（3分）种子；花朵**  \n\n");
        md.append("## 四、阅读理解（第7~8题，共6分）\n\n");
        md.append("**7.（3分）答：×**  \n\n");
        md.append("**8.（3分）答：√**  \n\n");
        md.append("## 五、看图写话（第9题，共10分）\n\n");
        md.append("**9.（10分）示例：**  \n");
        md.append("蓝天是白云的家。  \n");
        md.append("评分标准：内容切题得满分。\n");

        Map<Integer, AnswerKeyParser.QuestionKey> parsed = AnswerKeyParser.parse(md.toString());

        assertThat(parsed).as("8 道行内题号 + 1 道看图写话 = 9 项，区间标题不贡献幻影项").hasSize(9);
        // 每一 seq 都对应正确的印刷题号内容
        assertThat(parsed.get(1).answer()).isEqualTo("A");
        assertThat(parsed.get(8).answer()).isEqualTo("答：√");
        AnswerKeyParser.QuestionKey q9 = parsed.get(9);
        assertThat(q9).isNotNull();
        assertThat(q9.answer()).contains("蓝天是白云的家");
        assertThat(q9.scoringCriteria()).contains("内容切题");
    }

    @Test
    @DisplayName("行内题号末尾仅裸「示例：」标签、正文另起一行 → 正文归入答案（语文 Q32 看图写话）")
    void trailingBareExampleLabelCollectsFollowingBody() {
        String md =
                "## 五、看图写话（第32题，共10分）\n\n"
                        + "**32.（10分）示例：**  \n"
                        + "蓝天是白云的家。树林是小鸟的家。小河是鱼儿的家。  \n"
                        + "答案不唯一，能围绕图上景物写即可。  \n\n"
                        + "评分标准：  \n"
                        + "- 内容切题得 3~4 分。  \n"
                        + "- 正确使用句式，用对一句得 1 分。  \n"
                        + "满分10分。\n";

        Map<Integer, AnswerKeyParser.QuestionKey> parsed = AnswerKeyParser.parse(md);
        // 大题区间标题不算新题，仅 "32." 行头作为唯一边界 → seq=1
        AnswerKeyParser.QuestionKey key = parsed.get(1);

        assertThat(key).isNotNull();
        assertThat(key.answer())
                .as("行内仅剩「示例：」引导标签时，紧随其后的答案正文应被收集，而非丢弃")
                .contains("蓝天是白云的家")
                .contains("树林是小鸟的家")
                .contains("小河是鱼儿的家");
        assertThat(key.scoringCriteria()).as("评分标准仍独立归位，不并入答案").contains("内容切题").contains("正确使用句式");
    }
}
