package org.mpc.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import org.mpc.data.local.database.CoursePlanEntity
import org.mpc.data.local.database.CoursePlanItemEntity

@Dao
interface CoursePlanDao {
    @Query(
        """
            SELECT serialNo 
            FROM course_plan_item
            WHERE semester = :semester
        """,
    )
    suspend fun findSelectedSerialNumbers(semester: String): List<String>

    @Upsert
    suspend fun upsertPlan(entity: CoursePlanEntity)

    @Upsert
    suspend fun upsertItems(entity: List<CoursePlanItemEntity>)

    @Transaction
    suspend fun replaceItems(
        plan: CoursePlanEntity,
        items: List<CoursePlanItemEntity>,
    ) {
        upsertPlan(plan)
        deleteItems(plan.semester)
        upsertItems(items)
    }

    @Query("DELETE FROM course_plan WHERE semester = :semester")
    suspend fun deletePlan(semester: String)

    @Query("DELETE FROM course_plan_item WHERE semester = :semester")
    suspend fun deleteItems(semester: String)
}
