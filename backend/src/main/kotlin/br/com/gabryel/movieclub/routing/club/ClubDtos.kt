package br.com.gabryel.movieclub.routing.club

import kotlinx.serialization.Serializable

@Serializable
internal data class CreateClubRequest(
    val name: String,
)

@Serializable
internal data class ClubResponse(
    val id: String,
    val name: String,
)

@Serializable
internal data class ClubMemberResponse(
    val memberId: String,
    val name: String,
    val role: String,
    val rotationOrder: Int,
)

@Serializable
internal data class ClubDetailResponse(
    val id: String,
    val name: String,
    val preferredLanguages: List<String>,
    val ignoredLanguages: List<String>,
    val members: List<ClubMemberResponse>,
)

@Serializable
internal data class UpdateLanguagePreferencesRequest(
    val preferredLanguages: List<String>,
    val ignoredLanguages: List<String>,
)

@Serializable
internal data class AddClubMemberRequest(
    val memberId: String,
    val role: String = "MEMBER",
)

@Serializable
internal data class ChangeRoleRequest(
    val role: String,
)

@Serializable
internal data class UpdateRotationRequest(
    val memberIds: List<String>,
)

@Serializable
internal data class RatingOptionResponse(
    val id: String,
    val label: String,
    val position: Int,
    val color: String,
)

@Serializable
internal data class RatingScaleResponse(
    val id: String,
    val type: String,
    val options: List<RatingOptionResponse>,
)

@Serializable
internal data class UpdateRatingOptionRequest(
    val label: String? = null,
    val color: String? = null,
)

@Serializable
internal data class UpdateRatingOptionOrderRequest(
    val optionIds: List<String>,
)
