package org.mpc.domain.repository

import org.mpc.domain.model.CoursePlan

interface CoursePlanRepository {
    suspend fun loadPlan(semester: String): CoursePlan

    suspend fun savePlan(plan: CoursePlan)

    suspend fun deletePlan(semester: String)
}
