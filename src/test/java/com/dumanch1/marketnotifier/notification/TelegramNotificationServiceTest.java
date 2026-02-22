package com.dumanch1.marketnotifier.notification;

import com.dumanch1.marketnotifier.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TelegramNotificationService}.
 * Mocks the RestClient chain to verify correct API calls
 * and fault-tolerance behavior.
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TelegramNotificationService service;

    private static final TelegramProperties TEST_PROPS = new TelegramProperties(true, "test-bot-token", "12345");

    @BeforeEach
    void setUp() {
        // Mock RestClient.builder() static call so the constructor
        // returns our mocked RestClient
        RestClient.Builder mockBuilder = mock(RestClient.Builder.class);
        when(mockBuilder.baseUrl(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(restClient);

        try (MockedStatic<RestClient> staticMock = mockStatic(RestClient.class)) {
            staticMock.when(RestClient::builder).thenReturn(mockBuilder);
            service = new TelegramNotificationService(TEST_PROPS);
        }
    }

    @Test
    @DisplayName("Should call Telegram API with correct parameters")
    void sendsMessageToTelegramApi() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(), any(), any()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"ok\":true}");

        service.send("Test alert message");

        verify(restClient).post();
        verify(requestBodyUriSpec).uri(
                eq("/bot{token}/sendMessage?chat_id={chatId}&text={text}"),
                eq("test-bot-token"),
                eq("12345"),
                eq("Test alert message"));
        verify(requestBodySpec).retrieve();
    }

    @Test
    @DisplayName("Should not throw when Telegram API call fails")
    void doesNotThrowOnApiFailure() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(), any(), any()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(
                new org.springframework.web.client.RestClientException("Connection refused"));

        assertDoesNotThrow(() -> service.send("Test alert message"));
    }

    @Test
    @DisplayName("Should not throw when RestClient post() throws")
    void doesNotThrowOnPostFailure() {
        when(restClient.post()).thenThrow(new RuntimeException("Unexpected error"));

        assertDoesNotThrow(() -> service.send("Test alert message"));
    }
}
