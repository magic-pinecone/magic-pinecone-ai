package org.mpc.presentation.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import org.mpc.domain.model.PlaceCategory
import org.mpc.domain.model.PlaceTag
import org.mpc.shared.MR
import org.mpc.shared.campus_map_category_academic
import org.mpc.shared.campus_map_category_administration
import org.mpc.shared.campus_map_category_dining
import org.mpc.shared.campus_map_category_dormitory
import org.mpc.shared.campus_map_category_landmark
import org.mpc.shared.campus_map_category_library
import org.mpc.shared.campus_map_category_service
import org.mpc.shared.campus_map_category_shop
import org.mpc.shared.campus_map_category_sports
import org.mpc.shared.campus_map_category_transit
import org.mpc.shared.campus_map_tag_card_accepted
import org.mpc.shared.campus_map_tag_cash_only
import org.mpc.shared.campus_map_tag_english_menu
import org.mpc.shared.campus_map_tag_halal
import org.mpc.shared.campus_map_tag_late_night
import org.mpc.shared.campus_map_tag_open_24h
import org.mpc.shared.campus_map_tag_vegetarian

/** How a [PlaceCategory] looks on the map and in its chip. This is the only place that decides it. */
data class CategoryStyle(
    val label: StringResource,
    val icon: ImageVector,
    val color: Color,
)

data class TagStyle(
    val label: StringResource,
    val icon: ImageVector,
)

val PlaceCategory.style: CategoryStyle
    get() = when (this) {
        PlaceCategory.ACADEMIC -> CategoryStyle(MR.strings.campus_map_category_academic, Icons.Filled.School, Color(0xFF3B6FD4))
        PlaceCategory.ADMINISTRATION -> CategoryStyle(MR.strings.campus_map_category_administration, Icons.Filled.AccountBalance, Color(0xFF6B5B95))
        PlaceCategory.LIBRARY -> CategoryStyle(MR.strings.campus_map_category_library, Icons.Filled.LocalLibrary, Color(0xFF8A5A2B))
        PlaceCategory.DORMITORY -> CategoryStyle(MR.strings.campus_map_category_dormitory, Icons.Filled.Bed, Color(0xFF2E8B8B))
        PlaceCategory.DINING -> CategoryStyle(MR.strings.campus_map_category_dining, Icons.Filled.Restaurant, Color(0xFFE0562B))
        PlaceCategory.SHOP -> CategoryStyle(MR.strings.campus_map_category_shop, Icons.Filled.ShoppingBag, Color(0xFFD6336C))
        PlaceCategory.SPORTS -> CategoryStyle(MR.strings.campus_map_category_sports, Icons.Filled.SportsBasketball, Color(0xFF2F9E44))
        PlaceCategory.SERVICE -> CategoryStyle(MR.strings.campus_map_category_service, Icons.Filled.RoomService, Color(0xFF0B7285))
        PlaceCategory.TRANSIT -> CategoryStyle(MR.strings.campus_map_category_transit, Icons.Filled.DirectionsBus, Color(0xFFC77D0A))
        PlaceCategory.LANDMARK -> CategoryStyle(MR.strings.campus_map_category_landmark, Icons.Filled.Place, Color(0xFF6C757D))
    }

val PlaceTag.style: TagStyle
    get() = when (this) {
        PlaceTag.VEGETARIAN -> TagStyle(MR.strings.campus_map_tag_vegetarian, Icons.Filled.Eco)
        PlaceTag.HALAL -> TagStyle(MR.strings.campus_map_tag_halal, Icons.Filled.VerifiedUser)
        PlaceTag.OPEN_24H -> TagStyle(MR.strings.campus_map_tag_open_24h, Icons.Filled.Schedule)
        PlaceTag.LATE_NIGHT -> TagStyle(MR.strings.campus_map_tag_late_night, Icons.Filled.NightsStay)
        PlaceTag.CASH_ONLY -> TagStyle(MR.strings.campus_map_tag_cash_only, Icons.Filled.Payments)
        PlaceTag.CARD_ACCEPTED -> TagStyle(MR.strings.campus_map_tag_card_accepted, Icons.Filled.CreditCard)
        PlaceTag.ENGLISH_MENU -> TagStyle(MR.strings.campus_map_tag_english_menu, Icons.Filled.Translate)
    }
