package org.mpc.domain.model

import kotlin.jvm.JvmInline

data class CourseSummary(
    val serialNo: CourseSerialNo,
    val classNo: String,
    val title: String,
    val credit: Double,
    val passwordCard: PasswordCardType,
    val teachers: List<String>,
    val classTimes: List<CourseTime>,
    val limitCnt: Int,
    val admitCnt: Int,
    val waitCnt: Int,
    val collegeName: String,
    val departmentName: String,
    val courseType: CourseType,
    val detailUrl: String,
) {
    // A scalar identity for native clients; value classes export as opaque objects.
    val serialNumber: String get() = serialNo.value
}

enum class PasswordCardType(
    val description: String,
) {
    ALL("全部"),
    OPTIONAL("部分"),
    NONE("無"),
}

enum class CourseType(
    val description: String,
) {
    REQUIRED("必修"),
    ELECTIVE("選修"),
    UNKNOWN("未知"),
}

@JvmInline
value class CourseSerialNo(
    val value: String,
) {
    init {
        require(
            value.matches(Regex("""\d{5}""")),
        )
    }
}

data class CourseTime(
    val day: CourseDay,
    val period: CoursePeriod,
)

enum class CourseDay(
    val description: String,
    val code: String,
    val order: Int,
) {
    MONDAY("一", "1", 1),
    TUESDAY("二", "2", 2),
    WEDNESDAY("三", "3", 3),
    THURSDAY("四", "4", 4),
    FRIDAY("五", "5", 5),
    SATURDAY("六", "6", 6),
    SUNDAY("日", "7", 7),
    UNKNOWN("無", "0", 8),
    ;

    companion object {
        /** 1 is Monday and 7 is Sunday; anything else is [UNKNOWN]. */
        fun fromIsoWeekday(isoWeekday: Int): CourseDay = entries.firstOrNull { it != UNKNOWN && it.order == isoWeekday } ?: UNKNOWN
    }
}

enum class CoursePeriod(
    val description: String,
    val order: Int,
) {
    ONE("1", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    NOON("Z", 5),
    FIVE("5", 6),
    SIX("6", 7),
    SEVEN("7", 8),
    EIGHT("8", 9),
    NINE("9", 10),
    A("A", 11),
    B("B", 12),
    C("C", 13),
    D("D", 14),
}
