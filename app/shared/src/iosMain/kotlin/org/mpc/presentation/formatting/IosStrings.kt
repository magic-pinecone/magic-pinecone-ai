package org.mpc.presentation.formatting

import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.PasswordCardType
import org.mpc.shared.MR
import org.mpc.shared.coursedetail_password_all
import org.mpc.shared.coursedetail_password_none
import org.mpc.shared.coursedetail_password_some

fun localized(resource: StringResource): String = StringDesc.Resource(resource).localized()

fun localizedFormatted(resource: StringResource, args: List<Any>): String = StringDesc.ResourceFormatted(resource, args.map(::nativeArg)).localized()

fun localizedPlural(resource: PluralsResource, quantity: Int, args: List<Any>): String = StringDesc.PluralFormatted(resource, quantity, args.map(::nativeArg)).localized()

fun PasswordCardType.localizedLabel(): String = localized(
    when (this) {
        PasswordCardType.ALL -> MR.strings.coursedetail_password_all
        PasswordCardType.OPTIONAL -> MR.strings.coursedetail_password_some
        PasswordCardType.NONE -> MR.strings.coursedetail_password_none
    },
)

/** Swift integers arrive boxed as Long, which `%d` placeholders would not format like an Int. */
private fun nativeArg(arg: Any): Any = if (arg is Long) arg.toInt() else arg
