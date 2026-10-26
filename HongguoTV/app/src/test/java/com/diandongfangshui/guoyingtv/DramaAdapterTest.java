package com.diandongfangshui.guoyingtv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DramaAdapterTest {
    private JSONObject makeSeries(String playbackUrl) throws Exception {
        JSONObject episode = new JSONObject()
                .put("episode_index", 1)
                .put("video_id", "mock-video-1");
        if (playbackUrl != null) episode.put("playback_url", playbackUrl);
        return new JSONObject()
                .put("series_id", "mock-series-1")
                .put("series_title", "测试短剧")
                .put("series_cover", "https://example.com/poster.jpg")
                .put("episodes", new JSONArray().put(episode));
    }

    @Test public void metadataOnlySeriesIsBrowseableButNotPlayable() throws Exception {
        Drama series = Drama.fromJson(makeSeries(null), 2);
        assertEquals("mock-series-1", series.id);
        assertEquals("测试短剧", series.title);
        assertEquals("mock-video-1", series.episodes.get(0).videoId);
        assertFalse(series.episodes.get(0).isPlayable());
        assertFalse(series.hasPlayableEpisodes());
        assertEquals(-1, series.firstPlayableEpisode(0));
        assertTrue(series.metadataAdapter);
        assertFalse(Drama.fromJson(series.toJson()).hasPlayableEpisodes());
    }

    @Test public void separatelyLicensedMediaUrlCanPlay() throws Exception {
        Drama series = Drama.fromJson(makeSeries("https://example.com/licensed/01.m3u8"), 2);
        assertTrue(series.hasPlayableEpisodes());
        assertEquals(0, series.firstPlayableEpisode(0));
        assertEquals(1, series.episodes.get(0).episodeIndex);
    }

    @Test public void videoModelIsNeverInterpretedAsMediaUrl() throws Exception {
        JSONObject item = makeSeries(null);
        item.getJSONArray("episodes").getJSONObject(0)
                .put("video_model", "{\\\"main_url\\\":\\\"https://example.com/unapproved.mp4\\\"}");
        Drama series = Drama.fromJson(item, 2);
        assertFalse(series.hasPlayableEpisodes());
    }

    @Test public void invalidEpisodeIdsAreRejected() throws Exception {
        JSONObject item = makeSeries(null);
        item.getJSONArray("episodes").getJSONObject(0).remove("video_id");
        try {
            Drama.fromJson(item, 2);
            fail("Missing video_id should be rejected");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("video_id"));
        }
    }

    @Test public void plainHttpMediaIsRejected() throws Exception {
        try {
            Drama.fromJson(makeSeries("http://example.com/1.mp4"), 2);
            fail("Plain HTTP should be rejected");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
    }

    @Test public void legacyVersionRemainsPlayable() throws Exception {
        JSONObject item = new JSONObject()
                .put("id", "legacy-one")
                .put("title", "旧版测试")
                .put("episodes", new JSONArray().put(
                        new JSONObject().put("url", "https://example.com/01.mp4")));
        Drama series = Drama.fromJson(item, 1);
        assertFalse(series.metadataAdapter);
        assertTrue(series.episodes.get(0).isPlayable());
        assertEquals(0, series.firstPlayableEpisode(0));
    }

    @Test public void legacyEmptyMediaMustFail() throws Exception {
        JSONObject item = new JSONObject()
                .put("id", "legacy-empty")
                .put("title", "旧版无地址")
                .put("episodes", new JSONArray().put(new JSONObject().put("url", "")));
        try {
            Drama.fromJson(item, 1);
            fail("Legacy requires playback URL");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
    }

    @Test public void duplicateIndexesFail() throws Exception {
        JSONObject item = makeSeries(null);
        JSONObject duplicate = new JSONObject()
                .put("episode_index", 1)
                .put("video_id", "mock-video-2");
        item.getJSONArray("episodes").put(duplicate);
        try {
            Drama.fromJson(item, 2);
            fail("Duplicate index should fail");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }
    }

    @Test public void urlPolicyRejectsUserinfoAndFragments() {
        assertFalse(UrlPolicy.isHttpsUrl("https://user:pass@example.com/test.mp4"));
        assertFalse(UrlPolicy.isHttpsUrl("https://example.com/test.mp4#fragment"));
        assertFalse(UrlPolicy.isHttpsUrl("file:///sdcard/video.mp4"));
        assertTrue(UrlPolicy.isHttpsUrl("https://cdn.example.com/video.m3u8"));
    }
}
