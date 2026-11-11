package org.mpc.presentation.model

import dev.icerock.moko.resources.StringResource
import org.mpc.shared.MR
import org.mpc.shared.intl_section_credits
import org.mpc.shared.intl_section_graduation
import org.mpc.shared.intl_section_guides
import org.mpc.shared.intl_section_service_learning

/** The pages for international students that the home screen links to. */
enum class InternationalPage(
    val title: StringResource,
) {
    GRADUATION_LANGUAGE(MR.strings.intl_section_graduation),
    GOV_GUIDES(MR.strings.intl_section_guides),
    SERVICE_LEARNING(MR.strings.intl_section_service_learning),
    CREDITS(MR.strings.intl_section_credits),
}
