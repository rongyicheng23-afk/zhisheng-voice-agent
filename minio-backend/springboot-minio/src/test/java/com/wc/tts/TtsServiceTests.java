package com.wc.tts;

import com.wc.tts.config.TtsProperties;
import com.wc.tts.model.TtsRequestLimits;
import com.wc.tts.model.TtsUpstreamException;
import com.wc.tts.service.impl.TtsServiceImpl;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TtsServiceTests {
    @Test void productionClientDoesNotRetryUncertainSynthesis() {
        var client = new com.wc.tts.config.TtsClientConfig(new TtsProperties()).ttsOkHttpClient();
        assertFalse(client.retryOnConnectionFailure());
    }
    private final MockMultipartFile audio = new MockMultipartFile("audio", "a.wav", "audio/wav", new byte[]{1});
    private TtsServiceImpl service(Interceptor interceptor) {
        return new TtsServiceImpl(new RestTemplate(), new OkHttpClient.Builder()
                .addInterceptor(interceptor).build(), new TtsProperties());
    }
    private Response response(Interceptor.Chain chain, int code, byte[] body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(ResponseBody.create(body, MediaType.parse("audio/wav"))).build();
    }
    @Test void countsUnicodeCodePointsAndRejectsOverLimit() {
        assertDoesNotThrow(() -> TtsRequestLimits.validateText("😀".repeat(500)));
        assertThrows(IllegalArgumentException.class, () -> TtsRequestLimits.validateText("中".repeat(501)));
        assertThrows(IllegalArgumentException.class, () -> TtsRequestLimits.validateText("  "));
    }
    @Test void rejectsEmptyAndOversizedAudio() {
        assertThrows(IllegalArgumentException.class, () -> TtsRequestLimits.validateAudio(
                new MockMultipartFile("audio", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> TtsRequestLimits.validateAudio(
                new MockMultipartFile("audio", new byte[20 * 1024 * 1024])));
    }
    @Test void includesMultipartOverheadInLimitBeforeNetwork() {
        AtomicInteger calls = new AtomicInteger();
        var service = service(chain -> { calls.incrementAndGet(); return response(chain, 200, new byte[100]); });
        var large = new MockMultipartFile("audio", "a.wav", "audio/wav", new byte[20 * 1024 * 1024 - 1]);
        assertThrows(IllegalArgumentException.class, () -> service.synthesize(large, "你好", null, null, null));
        assertEquals(0, calls.get());
    }
    @Test void upstreamStatusIsSafeAndNotRetried() {
        for (int status : new int[]{400, 413, 429, 503, 500}) {
            AtomicInteger calls = new AtomicInteger();
            var service = service(chain -> { calls.incrementAndGet(); return response(chain, status, "secret/internal/path".getBytes()); });
            var error = assertThrows(TtsUpstreamException.class, () -> service.synthesize(audio, "你好", null, null, null));
            assertEquals(status == 429 || status == 503 ? 503 : status == 500 ? 502 : status, error.getCode());
            assertFalse(error.getMessage().contains("secret"));
            assertFalse(error.getMessage().contains("http"));
            assertEquals(1, calls.get());
        }
    }
    @Test void timeoutDoesNotClaimBackendCancellation() {
        var service = service(chain -> { throw new SocketTimeoutException("secret/path"); });
        var error = assertThrows(TtsUpstreamException.class, () -> service.synthesize(audio, "你好", null, null, null));
        assertEquals(504, error.getCode());
        assertTrue(error.getMessage().contains("可能仍在处理"));
        assertFalse(error.getMessage().contains("secret"));
    }
    @Test void validatesResponseAndPreservesSuccessfulAudio() throws Exception {
        var bad = service(chain -> response(chain, 200, new byte[0]));
        assertThrows(TtsUpstreamException.class, () -> bad.synthesize(audio, "你好", null, null, null));
        byte[] bytes = new byte[100];
        var good = service(chain -> response(chain, 200, bytes));
        assertArrayEquals(bytes, good.synthesize(audio, "你好", null, null, null).getAudioBytes());
    }
}
