package org.mpc.domain.repository

import org.mpc.domain.model.AnnouncementFeed

interface AnnouncementRepository {
    suspend fun fetchAnnouncements(forceRefresh: Boolean = false): AnnouncementFeed
}
