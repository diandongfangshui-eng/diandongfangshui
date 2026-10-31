package com.diandongfangshui.guoyingtv;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackPolicyTest {
    @Test public void boundedRetryDoesNotRetryDeniedContent() {
        assertTrue(PlaybackPolicy.retry(0, 503));
        assertFalse(PlaybackPolicy.retry(2, 503));
        assertFalse(PlaybackPolicy.retry(0, 403));
        assertFalse(PlaybackPolicy.retry(0, 401));
    }
    @Test public void resumeRestartsCompletedEpisodesAndClampsInvalidPosition() {
        assertEquals(0, PlaybackPolicy.resume(-1, 10000));
        assertEquals(0, PlaybackPolicy.resume(9999, 10000));
        assertEquals(5000, PlaybackPolicy.resume(5000, 10000));
    }
    @Test public void expiringMediaIsRefreshedBeforePlayback() {
        ContentServices.Playback p = new ContentServices.Playback("https://example.com/video.mp4", 60000);
        assertTrue(p.usable(0));
        assertFalse(p.usable(30000));
    }
}
