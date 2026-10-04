package com.nazcarbon.music.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistReleaseParserTest {
    @Test fun `album cards without More links survive and shelf determines category`() {
        val page = JSONObject("""{"contents":[
          {"musicCarouselShelfRenderer":{
            "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Albums"}]}}},
            "contents":[{"musicTwoRowItemRenderer":{
              "title":{"runs":[{"text":"Steppers"}]},
              "subtitle":{"runs":[{"text":"Album • 2022"}]},
              "overlay":{"watchPlaylistEndpoint":{"playlistId":"OLAK-album"}}
            }}]}},
          {"musicCarouselShelfRenderer":{
            "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Singles & EPs"}]}}},
            "contents":[{"musicTwoRowItemRenderer":{
              "title":{"runs":[{"text":"Example"}]},
              "subtitle":{"runs":[{"text":"EP • 2024"}]},
              "overlay":{"watchPlaylistEndpoint":{"playlistId":"OLAK-ep"}}
            }}]}},
          {"musicCarouselShelfRenderer":{
            "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Playlists"}]}}},
            "contents":[{"musicTwoRowItemRenderer":{
              "title":{"runs":[{"text":"Unrelated playlist"}]},
              "overlay":{"watchPlaylistEndpoint":{"playlistId":"PL-unrelated"}}
            }}]}}
        ]}""")
        val releases = NewPipeMusicSource().parseArtistReleaseCards(page)
        assertEquals(listOf("Steppers", "Example"), releases.map { it.title })
        assertEquals(listOf("Album", "EP"), releases.map { it.kind })
        assertEquals(listOf(2022, 2024), releases.map { it.year })
        assertEquals("https://music.youtube.com/playlist?list=OLAK-album", releases.first().url)
    }
}
