package br.com.gabryel.movieclub.service.csvimport

import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeriesService
import java.io.InputStream
import kotlin.uuid.Uuid

data class ImportMemberMapping(
    val choiceInitial: String,
    val csvDisplayName: String,
    val memberId: Uuid,
)

data class ImportRowIssue(
    val row: Int,
    val reason: String,
)

data class ImportResult(
    val created: Int,
    val updated: Int,
    val skipped: List<ImportRowIssue>,
    val warnings: List<ImportRowIssue>,
)

class ImportService(
    private val clubService: ClubService,
    private val meetingRepository: MeetingRepository,
    private val movieRepository: MovieRepository,
    private val movieService: MovieService,
    private val seriesRepository: SeriesRepository,
    private val seriesService: SeriesService,
    private val seasonRepository: SeasonRepository,
    private val episodeRepository: EpisodeRepository,
    private val episodeService: EpisodeService,
    private val watchlistRepository: WatchlistRepository,
    private val ratingScaleRepository: RatingScaleRepository,
) {
    suspend fun importMovies(
        clubId: Uuid,
        actingMemberId: Uuid,
        input: InputStream,
        mappings: List<ImportMemberMapping>,
    ): ImportResult {
        clubService.requireAdmin(clubId, actingMemberId)
        val rows = MoviesCsvParser.parse(input)
        validateMappingCoverage(
            initials = rows.map { it.choiceInitial }.filter { it.isNotBlank() }.toSet(),
            displayNames = rows.flatMap { it.ratingsByDisplayName.keys }.toSet(),
            mappings = mappings,
        )
        val initialToMember = mappings.byInitial()
        val scalesByType = loadScalesWithOptions(clubId)

        var created = 0
        val skipped = mutableListOf<ImportRowIssue>()
        val warnings = mutableListOf<ImportRowIssue>()

        rows
            .filter { it.date == null }
            .forEach { skipped.add(ImportRowIssue(it.rowNumber, "No resolvable meeting date")) }

        rows.filter { it.date != null }.groupBy { it.date!! }.forEach { (date, group) ->
            val assignedMemberId = initialToMember[group.first().choiceInitial]
            val meeting = meetingRepository.findByClubAndDate(clubId, date) ?: meetingRepository.create(
                clubId,
                date,
                assignedMemberId,
            )

            group.forEach { row ->
                // no IMDB id: either a bare skeleton row (nothing chosen yet) or a pick not yet identified --
                // either way movies.imdb_id is required, so there's nothing to create
                val imdbId = row.imdbId ?: return@forEach
                val chosenById = initialToMember[row.choiceInitial]
                if (chosenById == null) {
                    skipped.add(ImportRowIssue(row.rowNumber, "Unmapped Choice initial '${row.choiceInitial}'"))
                    return@forEach
                }

                val existing = movieRepository.findByMeetingAndImdbId(meeting.id, imdbId)
                val movie = if (existing != null) {
                    skipped.add(ImportRowIssue(row.rowNumber, "already imported"))
                    existing
                } else {
                    created++
                    // originalTitle is NOT NULL, but nothing here can supply it -- the best-effort refresh below
                    // fetches everything (title included) from TMDB, so this is only a placeholder until then
                    val placeholderMetadata = TmdbMovieMetadata(
                        originalTitle = imdbId,
                        alternativeTitles = emptyList(),
                    )
                    val inserted =
                        movieRepository.create(meeting.id, chosenById, imdbId, placeholderMetadata, row.watchLink)

                    runCatching { movieService.refreshMetadata(inserted.id, actingMemberId) }.getOrElse {
                        warnings.add(ImportRowIssue(row.rowNumber, "TMDB refresh failed: ${it.message}"))
                        inserted
                    }
                }

                applyRatings(
                    row.ratingsByDisplayName,
                    mappings.byDisplayName(),
                    scalesByType,
                    warnings,
                    row.rowNumber,
                ) { memberId, quality, sentiment ->
                    movieRepository.upsertReview(movie.id, memberId, quality, sentiment)
                }
            }
        }

        return ImportResult(created, 0, skipped, warnings)
    }

    suspend fun importSeries(
        clubId: Uuid,
        actingMemberId: Uuid,
        input: InputStream,
        mappings: List<ImportMemberMapping>,
    ): ImportResult {
        clubService.requireAdmin(clubId, actingMemberId)
        val blocks = SeriesCsvParser.parse(input)
        validateMappingCoverage(
            initials = blocks.map { it.header.choiceInitial }.toSet(),
            displayNames = blocks.flatMap { it.header.ratingsByDisplayName.keys }.toSet(),
            mappings = mappings,
        )

        val initialToMember = mappings.byInitial()
        val nameToMember = mappings.byDisplayName()
        val scalesByType = loadScalesWithOptions(clubId)

        var created = 0
        val skipped = mutableListOf<ImportRowIssue>()
        val warnings = mutableListOf<ImportRowIssue>()

        blocks.forEach { block ->
            val header = block.header
            val imdbId = header.imdbId
            if (imdbId == null) {
                // Series.csv has no IMDB Id column yet; series.imdb_id is NOT NULL (same rule as movies), so this
                // whole series is skipped until the user adds the column and re-runs the import.
                skipped.add(
                    ImportRowIssue(
                        header.rowNumber,
                        "Missing IMDB Id for series '${header.title}' -- add the IMDB Id column to import",
                    ),
                )
                return@forEach
            }
            val chosenById = initialToMember[header.choiceInitial]
            if (chosenById == null) {
                skipped.add(ImportRowIssue(header.rowNumber, "Unmapped Choice initial '${header.choiceInitial}'"))
                return@forEach
            }

            val existingSeries = seriesRepository.findByClubAndImdbId(clubId, imdbId)
            val series = if (existingSeries != null) {
                skipped.add(ImportRowIssue(header.rowNumber, "already imported"))
                existingSeries
            } else {
                created++
                val csvMetadata = TmdbSeriesMetadata(
                    originalTitle = header.title,
                    alternativeTitles = emptyList(),
                )

                val inserted = seriesRepository.create(clubId, chosenById, imdbId, csvMetadata)
                runCatching { seriesService.refreshMetadata(inserted.id, actingMemberId) }.getOrElse {
                    warnings.add(
                        ImportRowIssue(header.rowNumber, "TMDB refresh failed: ${it.message}"),
                    )
                    inserted
                }
            }

            applyRatings(
                header.ratingsByDisplayName,
                nameToMember,
                scalesByType,
                warnings,
                header.rowNumber,
            ) { memberId, quality, sentiment ->
                seriesRepository.upsertReview(series.globalSeriesId, memberId, quality, sentiment)
            }

            // Best effort: pulls the entire season/episode catalog for this series from TMDB up front, so CSV rows
            // below are matched against real (season, episode) numbers instead of defining the catalog themselves.
            // If TMDB matching fails (series not found, etc.), fall back to creating seasons/episodes from the CSV
            // alone, exactly as before this existed.
            val bulkImported = runCatching { seriesService.importSeasonsAndEpisodes(series.id, actingMemberId) }
                .onSuccess { created += it }
                .onFailure {
                    warnings.add(
                        ImportRowIssue(header.rowNumber, "Could not import full series from TMDB: ${it.message}"),
                    )
                }
                .isSuccess

            block.seasons.forEach seasonBlock@{ seasonBlock ->
                val existingSeason = seasonRepository
                    .listBySeries(series.globalSeriesId).find { it.number == seasonBlock.header.number }

                val season = existingSeason ?: if (bulkImported) {
                    warnings.add(
                        ImportRowIssue(
                            seasonBlock.header.rowNumber,
                            "Season ${seasonBlock.header.number} not found in TMDB for this series",
                        ),
                    )
                    return@seasonBlock
                } else {
                    seasonRepository.create(series.globalSeriesId, seasonBlock.header.number).also { created++ }
                }

                applyRatings(
                    seasonBlock.header.ratingsByDisplayName,
                    nameToMember,
                    scalesByType,
                    warnings,
                    seasonBlock.header.rowNumber,
                ) { memberId, quality, sentiment ->
                    seasonRepository.upsertReview(season.id, memberId, quality, sentiment)
                }

                seasonBlock.episodes.forEach episodeRow@{ episodeRow ->
                    val existingEpisode =
                        episodeRepository.listBySeason(season.id).find { it.number == episodeRow.number }

                    val meetingId = episodeRow.date?.let { date ->
                        (meetingRepository.findByClubAndDate(clubId, date) ?: meetingRepository.create(clubId, date))
                            .id
                    }

                    val episode = existingEpisode ?: if (bulkImported) {
                        warnings.add(
                            ImportRowIssue(
                                episodeRow.rowNumber,
                                "Episode ${episodeRow.number} not found in TMDB for season ${seasonBlock.header.number}",
                            ),
                        )
                        return@episodeRow
                    } else {
                        val inserted = episodeRepository.create(season.id, episodeRow.number, episodeRow.title)
                        created++
                        runCatching { episodeService.refreshMetadata(inserted.id, actingMemberId) }.getOrElse {
                            warnings.add(
                                ImportRowIssue(episodeRow.rowNumber, "TMDB refresh failed: ${it.message}"),
                            )
                            inserted
                        }
                    }

                    // Runs unconditionally (not just on first creation) -- under bulk import, an episode is
                    // typically already "found" (pre-created by importSeasonsAndEpisodes), so this is the only
                    // place a CSV row's meeting assignment actually gets applied. assignToMeeting is idempotent.
                    if (meetingId != null) episodeRepository.assignToMeeting(episode.id, meetingId)

                    applyRatings(
                        episodeRow.ratingsByDisplayName,
                        nameToMember,
                        scalesByType,
                        warnings,
                        episodeRow.rowNumber,
                    ) { memberId, quality, sentiment ->
                        episodeRepository.upsertReview(episode.id, memberId, quality, sentiment)
                    }
                }
            }
        }

        return ImportResult(created, 0, skipped, warnings)
    }

    fun importReserve(
        clubId: Uuid,
        actingMemberId: Uuid,
        input: InputStream,
        mappings: List<ImportMemberMapping>,
    ): ImportResult {
        clubService.requireAdmin(clubId, actingMemberId)
        val rows = ReserveCsvParser.parse(input)
        validateMappingCoverage(
            initials = emptySet(),
            displayNames = rows.map { it.csvDisplayName }.toSet(),
            mappings = mappings,
        )
        val nameToMember = mappings.byDisplayName()

        var created = 0
        val skipped = mutableListOf<ImportRowIssue>()
        val existingByClub = watchlistRepository.listByClub(clubId)

        rows.forEachIndexed { index, row ->
            val memberId = nameToMember.getValue(row.csvDisplayName)
            val alreadyExists = existingByClub.any { it.memberId == memberId && it.title == row.title }
            if (alreadyExists) {
                skipped.add(ImportRowIssue(index + 1, "already imported"))
            } else {
                watchlistRepository.create(clubId, memberId, row.title)
                created++
            }
        }

        return ImportResult(created, 0, skipped, emptyList())
    }

    private fun loadScalesWithOptions(clubId: Uuid): Map<RatingScaleType, Map<String, Uuid>> =
        ratingScaleRepository.findScales(clubId).associate { scale ->
            scale.type to ratingScaleRepository.findOptions(scale.id).associate { it.label to it.id }
        }

    private fun validateMappingCoverage(
        initials: Set<String>,
        displayNames: Set<String>,
        mappings: List<ImportMemberMapping>,
    ) {
        val missingInitials = initials - mappings.map { it.choiceInitial }.toSet()
        val missingNames = displayNames - mappings.map { it.csvDisplayName }.toSet()

        if (missingInitials.isNotEmpty() || missingNames.isNotEmpty())
            throw BadRequestException("Unmapped identities in CSV: initials=$missingInitials, displayNames=$missingNames")
    }

    private fun applyRatings(
        ratingsByDisplayName: Map<String, RatingPair>,
        nameToMember: Map<String, Uuid>,
        scalesByType: Map<RatingScaleType, Map<String, Uuid>>,
        warnings: MutableList<ImportRowIssue>,
        rowNumber: Int,
        upsert: (memberId: Uuid, qualityOptionId: Uuid?, sentimentOptionId: Uuid?) -> Unit,
    ) {
        ratingsByDisplayName.forEach { (displayName, pair) ->
            if (pair.qualityLabel == null && pair.sentimentLabel == null)
                return@forEach

            val memberId = nameToMember[displayName]
            if (memberId == null) {
                warnings.add(ImportRowIssue(rowNumber, "Unmapped rating column '$displayName'"))
                return@forEach
            }

            val qualityOptionId = pair.qualityLabel?.let { label ->
                scalesByType[QUALITY]?.get(label) ?: run {
                    warnings.add(ImportRowIssue(rowNumber, "Unknown quality label '$label'"))
                    null
                }
            }
            val sentimentOptionId = pair.sentimentLabel?.let { label ->
                scalesByType[SENTIMENT]?.get(label) ?: run {
                    warnings.add(ImportRowIssue(rowNumber, "Unknown sentiment label '$label'"))
                    null
                }
            }
            if (qualityOptionId != null || sentimentOptionId != null)
                upsert(memberId, qualityOptionId, sentimentOptionId)
        }
    }

    private fun List<ImportMemberMapping>.byInitial(): Map<String, Uuid> = associate { it.choiceInitial to it.memberId }

    private fun List<ImportMemberMapping>.byDisplayName(): Map<String, Uuid> =
        associate { it.csvDisplayName to it.memberId }
}
