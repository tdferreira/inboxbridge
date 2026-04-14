package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TransitSecretProviderTest {

    @Test
    void healthReportsReadyWhenTransitKeyMetadataIsReachable() {
        TransitSecretProvider provider = provider(new StubHttpClient(
                200,
                "{\"data\":{\"name\":\"inboxbridge\"}}"));

        SecretProviderHealth health = provider.health(config());

        assertTrue(health.healthy());
        assertTrue(health.writable());
        assertEquals("OPENBAO_TRANSIT transit provider is ready.", health.statusMessage());
    }

    @Test
    void encryptPostsBase64PlaintextAndContextToTransitEndpoint() {
        AtomicReference<HttpRequest> requestRef = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        TransitSecretProvider provider = provider(new StubHttpClient(
                200,
                "{\"data\":{\"ciphertext\":\"vault:v1:opaque\"}}",
                requestRef,
                requestBody));

        SecretEncryptionService.EncryptedValue encrypted = provider.encrypt(config(), "secret-value", "provider:subject:refresh");

        assertEquals("vault:v1:opaque", encrypted.ciphertextBase64());
        assertEquals("", encrypted.nonceBase64());
        assertEquals(URI.create("https://transit.example.internal/v1/transit/encrypt/inboxbridge"), requestRef.get().uri());
        assertTrue(requestBody.get().contains("\"plaintext\":\"c2VjcmV0LXZhbHVl\""));
        assertTrue(requestBody.get().contains("\"context\":\"cHJvdmlkZXI6c3ViamVjdDpyZWZyZXNo\""));
    }

    @Test
    void decryptPostsCiphertextAndContextAndReturnsDecodedPlaintext() {
        AtomicReference<HttpRequest> requestRef = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        TransitSecretProvider provider = provider(new StubHttpClient(
                200,
                "{\"data\":{\"plaintext\":\"c2VjcmV0LXZhbHVl\"}}",
                requestRef,
                requestBody));

        String decrypted = provider.decrypt(config(), "vault:v1:opaque", "provider:subject:refresh");

        assertEquals("secret-value", decrypted);
        assertEquals(URI.create("https://transit.example.internal/v1/transit/decrypt/inboxbridge"), requestRef.get().uri());
        assertTrue(requestBody.get().contains("\"ciphertext\":\"vault:v1:opaque\""));
        assertTrue(requestBody.get().contains("\"context\":\"cHJvdmlkZXI6c3ViamVjdDpyZWZyZXNo\""));
    }

    private TransitSecretProvider provider(HttpClient httpClient) {
        TransitSecretProvider provider = new TransitSecretProvider();
        provider.setObjectMapper(new ObjectMapper());
        provider.setHttpClient(httpClient);
        return provider;
    }

    private TransitProviderConfig config() {
        return new TransitProviderConfig(
                SecretProviderMode.OPENBAO_TRANSIT,
                "OPENBAO_TRANSIT",
                "https://transit.example.internal/",
                "token",
                "transit",
                "inboxbridge");
    }

    private static final class StubHttpClient extends HttpClient {
        private final int statusCode;
        private final String responseBody;
        private final AtomicReference<HttpRequest> requestRef;
        private final AtomicReference<String> requestBodyRef;

        private StubHttpClient(int statusCode, String responseBody) {
            this(statusCode, responseBody, new AtomicReference<>(), new AtomicReference<>());
        }

        private StubHttpClient(
                int statusCode,
                String responseBody,
                AtomicReference<HttpRequest> requestRef,
                AtomicReference<String> requestBodyRef) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.requestRef = requestRef;
            this.requestBodyRef = requestBodyRef;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requestRef.set(request);
            requestBodyRef.set(readRequestBody(request));
            HttpResponse.ResponseInfo responseInfo = new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return statusCode;
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(java.util.Map.of("Content-Type", List.of("application/json")), (left, right) -> true);
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(responseInfo);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
            subscriber.onComplete();
            return new StubHttpResponse<>(request, subscriber.getBody().toCompletableFuture().join(), statusCode);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync is not used in these tests");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync is not used in these tests");
        }

        private String readRequestBody(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElse(null);
            if (publisher == null) {
                return "";
            }
            BodyCollector collector = new BodyCollector();
            publisher.subscribe(collector);
            return collector.body();
        }
    }

    private static final class BodyCollector implements Flow.Subscriber<ByteBuffer> {
        private final StringBuilder body = new StringBuilder();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            body.append(StandardCharsets.UTF_8.decode(item.duplicate()));
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException("Failed to read request body", throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return body.toString();
        }
    }

    private record StubHttpResponse<T>(HttpRequest request, T body, int statusCode) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of("Content-Type", List.of("application/json")), (left, right) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
