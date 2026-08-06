package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.Table

object MemberSeriesReviews : Table("member_series_reviews") {
    val seriesId = reference("series_id", Series)
    val memberId = reference("member_id", Members)
    val qualityOptionId = optReference("quality_option_id", RatingOptions)
    val sentimentOptionId = optReference("sentiment_option_id", RatingOptions)
    val comment = text("comment").nullable()

    override val primaryKey = PrimaryKey(seriesId, memberId)
}

object MemberSeasonReviews : Table("member_season_reviews") {
    val seasonId = reference("season_id", Seasons)
    val memberId = reference("member_id", Members)
    val qualityOptionId = optReference("quality_option_id", RatingOptions)
    val sentimentOptionId = optReference("sentiment_option_id", RatingOptions)
    val comment = text("comment").nullable()

    override val primaryKey = PrimaryKey(seasonId, memberId)
}

object MemberEpisodeReviews : Table("member_episode_reviews") {
    val episodeId = reference("episode_id", Episodes)
    val memberId = reference("member_id", Members)
    val qualityOptionId = optReference("quality_option_id", RatingOptions)
    val sentimentOptionId = optReference("sentiment_option_id", RatingOptions)
    val comment = text("comment").nullable()

    override val primaryKey = PrimaryKey(episodeId, memberId)
}
