package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.Table

object MemberMovieReviews : Table("member_movie_reviews") {
    val movieId = reference("movie_id", Movies)
    val memberId = reference("member_id", Members)
    val qualityOptionId = optReference("quality_option_id", RatingOptions)
    val sentimentOptionId = optReference("sentiment_option_id", RatingOptions)
    val comment = text("comment").nullable()

    override val primaryKey = PrimaryKey(movieId, memberId)
}
