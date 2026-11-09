package org.mpc.data.dto

import kotlinx.serialization.Serializable

/*
{
  "serial_no": "00001",
  "objectives": "本課程為基礎課程，修完本課程，學生應能:\n1.具有五十音聽說讀寫能力。\n2.流利表達自我介紹、日常問候語。\n3.習得日期時間、物品、地點等名詞及常用動詞語彙。會表達與生活相關的名詞句型與動詞句型。\n4.培養進一步向上學習的興趣。具備繼續學習進階日語的能力。",
  "content": "以「大家的日本語初級I」為主要教材，課程範圍第1課到第5課。學習日常生活必備基本語彙及基本句型表達。\n1.發音：逐字練習五十音，加強個別發音及書寫練習。於發音課程中導入單字，學習日常問候語。\n2.各課：認識單字、理解句型、組合使用、個別口頭練習、分組會話演練。\n(1)第1課:我是學生。 ∕自我介紹。\n(2)第2課:那是甚麼? ∕是誰的呢?\n(3)第3課:我的學校。∕車站在哪裡呢?\n(4)第4課:幾點起床呢? ∕田中先生的一天。\n(5)第5課:去、來、回家。\n3.教學方式：認識單字、講解句型。使用圖片﹑實物及視聽教材輔助教學，以互動方式練習會話，熟練各課基本句型，反覆開口說日語。",
  "books": "『大家的日本語初級１』改訂版（大新書局）",
  "teaching_method": "講授\n\n個別指導",
  "grading_policy": "1.第 9週:期中考30％\n2.第16週:期末考30％\n3.平常考與出席課堂表現40％",
  "distribution_conditions": [
    {
      "priority": 1,
      "rule": "學制:限學士班。"
    }
  ]
}
 */
@Serializable
internal data class CourseDetailDto(
    val serialNo: String,
    val objectives: String,
    val content: String,
    val books: String,
    val teachingMethod: String,
    val gradingPolicy: String,
    val distributionConditions: List<DistributionConditionDto>,
)

@Serializable
internal data class DistributionConditionDto(
    val priority: Int,
    val rule: String,
)
