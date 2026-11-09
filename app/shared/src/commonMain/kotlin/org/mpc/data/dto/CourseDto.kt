package org.mpc.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
{
      "serial_no": "01001",
      "class_no": "PE1011-A",
      "title": "大一體育",
      "credit": 0.0,
      "password_card": "OPTIONAL",
      "teachers": [
        "沈淑貞"
      ],
      "class_times": [
        "1-3",
        "1-4"
      ],
      "limit_cnt": 0,
      "admit_cnt": 0,
      "wait_cnt": 0,
      "college_name": "中心、處室",
      "department_name": "體育室",
      "course_type": "REQUIRED",
      "detail_url": "https://cis.ncu.edu.tw/Course/main/support/courseDetail.html?crs=01001"
    },
 */
@Serializable
internal data class CourseDto(
    val serialNo: String,
    val classNo: String,
    val title: String,
    val credit: Double,
    val passwordCard: PasswordCardTypeDto,
    val teachers: List<String>,
    val classTimes: List<String>,
    val limitCnt: Int,
    val admitCnt: Int,
    val waitCnt: Int,
    val collegeName: String,
    val departmentName: String,
    val courseType: CourseTypeDto,
    val detailUrl: String,
)

@Serializable
internal enum class PasswordCardTypeDto {
    @SerialName("ALL")
    ALL,

    @SerialName("OPTIONAL")
    OPTIONAL,

    @SerialName("NONE")
    NONE,
}

@Serializable
internal enum class CourseTypeDto {
    @SerialName("REQUIRED")
    REQUIRED,

    @SerialName("ELECTIVE")
    ELECTIVE,

    @SerialName("UNKNOWN")
    UNKNOWN,
}
