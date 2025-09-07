package com.barkosoft.router.service;

import com.barkosoft.router.dto.BatchResult;
import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteOptimizationMessage;
import com.barkosoft.router.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaRouteConsumerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private JobTrackingService jobTrackingService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private KafkaRouteConsumer kafkaRouteConsumer;

    private RouteOptimizationMessage message;

    @BeforeEach
    void setUp() {
        // Customer nesnesini doğru şekilde, setter metotları kullanarak oluştur
        Customer customer = new Customer();
        customer.setMyId(1L);
        customer.setLatitude(41.1);
        customer.setLongitude(29.1);

        message = new RouteOptimizationMessage();
        message.setJobId("test-job-1");
        message.setBatchIndex(0);
        message.setStartLatitude(41.0);
        message.setStartLongitude(29.0);
        message.setBatch(Arrays.asList(customer));
    }

    @Test
    void shouldProcessBatchSuccessfully() {
        // Arrange: Başarılı bir RouteResponse hazırla
        RouteResponse mockResponse = new RouteResponse();
        mockResponse.setOptimizedCustomerIds(Arrays.asList(1L));
        mockResponse.setTotalDistance("10,500 km");

        when(routeService.optimizeSingleBatch(anyDouble(), anyDouble(), any(List.class))).thenReturn(mockResponse);

        // Act: Consumer'ın process metodunu çağır
        kafkaRouteConsumer.processBatch(message, "test-topic", 0, acknowledgment);

        // Assert: Beklenen metotların çağrıldığını ve sonuçların doğru olduğunu doğrula
        verify(routeService, times(1)).optimizeSingleBatch(eq(41.0), eq(29.0), any(List.class));

        ArgumentCaptor<BatchResult> batchResultCaptor = ArgumentCaptor.forClass(BatchResult.class);
        verify(jobTrackingService, times(1)).addBatchResult(batchResultCaptor.capture());

        BatchResult capturedResult = batchResultCaptor.getValue();
        assertTrue(capturedResult.isSuccess());
        assertEquals("test-job-1", capturedResult.getJobId());
        assertEquals(10.5, capturedResult.getDistanceKm());
        assertFalse(capturedResult.getOptimizedCustomerIds().isEmpty());

        verify(acknowledgment, times(1)).acknowledge(); // Kafka mesajının işlendi olarak işaretlendiğini doğrula
    }

    @Test
    void shouldHandleExceptionDuringProcessing() {
        // Arrange: RouteService'in hata fırlatmasını sağla
        when(routeService.optimizeSingleBatch(anyDouble(), anyDouble(), any(List.class)))
                .thenThrow(new RuntimeException("OSRM API is down"));

        // Act: Consumer'ın process metodunu çağır
        kafkaRouteConsumer.processBatch(message, "test-topic", 0, acknowledgment);

        // Assert: Hata durumunda beklenen metotların çağrıldığını doğrula
        verify(routeService, times(1)).optimizeSingleBatch(anyDouble(), anyDouble(), any(List.class));

        ArgumentCaptor<BatchResult> batchResultCaptor = ArgumentCaptor.forClass(BatchResult.class);
        verify(jobTrackingService, times(1)).addBatchResult(batchResultCaptor.capture());

        BatchResult capturedResult = batchResultCaptor.getValue();
        assertFalse(capturedResult.isSuccess()); // Sonucun başarısız olduğunu kontrol et
        assertEquals("OSRM API is down", capturedResult.getErrorMessage());
        assertEquals(1L, capturedResult.getOptimizedCustomerIds().get(0)); // Hata durumunda müşteri ID'lerinin korunduğunu kontrol et

        verify(acknowledgment, times(1)).acknowledge(); // Hata yönetilse bile mesajın işlendi olarak işaretlendiğini doğrula
    }

    @Test
    void shouldUsePreviousBatchLastCoordinatesWhenAvailable() {
        // Arrange: Mesaja bir önceki batch'in son konum bilgilerini ekle
        message.setPreviousBatchLastLat(41.5);
        message.setPreviousBatchLastLng(29.5);

        RouteResponse mockResponse = new RouteResponse();
        mockResponse.setTotalDistance("1,000 km");
        // DÜZELTME: NullPointerException'ı önlemek için boş bir liste ata
        mockResponse.setOptimizedCustomerIds(new ArrayList<>());

        when(routeService.optimizeSingleBatch(anyDouble(), anyDouble(), any(List.class))).thenReturn(mockResponse);

        // Act: Consumer'ın process metodunu çağır
        kafkaRouteConsumer.processBatch(message, "test-topic", 0, acknowledgment);

        // Assert: RouteService'in, orijinal başlangıç noktası yerine bir önceki batch'in son konumuyla çağrıldığını doğrula
        verify(routeService, times(1)).optimizeSingleBatch(eq(41.5), eq(29.5), any(List.class));
        // TooManyActualInvocations hatasını önlemek için bu doğrulamanın 1 kez çağrıldığından emin ol
        verify(jobTrackingService, times(1)).addBatchResult(any(BatchResult.class));
        verify(acknowledgment, times(1)).acknowledge();
    }
}