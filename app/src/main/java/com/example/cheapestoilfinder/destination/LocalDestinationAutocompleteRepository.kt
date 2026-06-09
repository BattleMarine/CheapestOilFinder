package com.example.cheapestoilfinder.destination

import com.example.cheapestoilfinder.station.api.ApiCallback
import java.util.Locale

class LocalDestinationAutocompleteRepository : DestinationAutocompleteRepository {
    private val suggestions = listOf(
        suggestion("삼성역", "서울 강남구 테헤란로"),
        suggestion("선릉역", "서울 강남구 테헤란로"),
        suggestion("역삼역", "서울 강남구 역삼동"),
        suggestion("강남역", "서울 강남구 강남대로"),
        suggestion("서울역", "서울 중구 한강대로"),
        suggestion("용산역", "서울 용산구 한강대로"),
        suggestion("여의도역", "서울 영등포구 국제금융로"),
        suggestion("홍대입구역", "서울 마포구 양화로"),
        suggestion("잠실역", "서울 송파구 올림픽로"),
        suggestion("수서역", "서울 강남구 밤고개로"),
        suggestion("판교역", "경기 성남시 분당구 판교역로"),
        suggestion("코엑스", "서울 강남구 영동대로"),
        suggestion("세빛섬", "서울 서초구 올림픽대로"),
        suggestion("가로수길", "서울 강남구 신사동"),
        suggestion("압구정역", "서울 강남구 압구정로"),
        suggestion("청담역", "서울 강남구 학동로"),
        suggestion("동대문역사문화공원역", "서울 중구 을지로"),
        suggestion("신촌역", "서울 서대문구 신촌로"),
        suggestion("건대입구역", "서울 광진구 능동로"),
        suggestion("사당역", "서울 동작구 동작대로"),
        suggestion("대치동", "서울 강남구 대치동"),
        suggestion("서초동", "서울 서초구 서초동")
    )

    override fun search(
        query: String,
        callback: ApiCallback<List<DestinationSearchSuggestion>>
    ) {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < 2) {
            callback.onSuccess(emptyList())
            return
        }

        val results = suggestions
            .mapNotNull { item ->
                val titleScore = score(normalizedQuery, item.displayText)
                val descriptionScore = score(normalizedQuery, item.description)
                val tokenScore = item.searchTokens.minOfOrNull { score(normalizedQuery, it) } ?: Int.MAX_VALUE
                val bestScore = minOf(titleScore, descriptionScore, tokenScore)
                if (bestScore == Int.MAX_VALUE) null else item to bestScore
            }
            .sortedWith(
                compareBy<Pair<DestinationSearchSuggestion, Int>> { it.second }
                    .thenBy { it.first.displayText.length }
                    .thenBy { it.first.displayText }
            )
            .map { it.first }
            .take(4)

        callback.onSuccess(results)
    }

    private fun suggestion(displayText: String, description: String): DestinationSearchSuggestion {
        return DestinationSearchSuggestion(
            displayText = displayText,
            description = description,
            searchTokens = listOf(displayText, description)
        )
    }

    private fun score(query: String, candidate: String): Int {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) {
            return Int.MAX_VALUE
        }

        return when {
            normalizedCandidate == query -> 0
            normalizedCandidate.startsWith(query) -> 1
            normalizedCandidate.contains(query) -> 2
            query.contains(normalizedCandidate) -> 3
            else -> Int.MAX_VALUE
        }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.KOREA)
            .replace(Regex("[\\s\\p{Punct}]"), "")
    }
}
