package org.jack.wealthflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.exception.GlobalExceptionHandler;
import org.jack.wealthflow.model.AssetSnapshotBatchEntry;
import org.jack.wealthflow.service.AssetSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖批量创建快照端点：DTO 反序列化与模型转换、空 body 校验、
 * 业务异常经 GlobalExceptionHandler 统一返回。
 */
@ExtendWith(MockitoExtension.class)
class AssetSnapshotControllerTest {

    @Mock
    private AssetSnapshotService assetSnapshotService;

    /**
     * 与运行时 Spring Boot 注入的 ObjectMapper 保持一致的日期序列化
     * （ISO 字符串而非时间戳数组）。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AssetSnapshotController(assetSnapshotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    private AssetSnapshotResponse response(
            Long id,
            LocalDate date,
            Long categoryId,
            String categoryName,
            String amount
    ) {
        SnapshotItem item = new SnapshotItem();
        item.setCategoryId(categoryId);
        item.setCategoryName(categoryName);
        item.setAmount(new BigDecimal(amount));

        AssetSnapshotResponse response = new AssetSnapshotResponse();
        response.setId(id);
        response.setSnapshotDate(date);
        response.setItems(List.of(item));
        response.setTotalAmount(new BigDecimal(amount));
        return response;
    }

    @Test
    void shouldBatchCreateAndReturn200InEntryOrder() throws Exception {
        when(assetSnapshotService.batchSave(any()))
                .thenReturn(List.of(
                        response(1L, LocalDate.of(2026, 8, 1), 1L, "现金", "1000.00"),
                        response(2L, LocalDate.of(2026, 8, 2), 2L, "股票", "2000.00")
                ));

        mockMvc.perform(post("/api/v1/snapshots/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    {
                                      "snapshotDate": "2026-08-01",
                                      "items": [
                                        { "categoryId": 1, "amount": "1000.00" }
                                      ]
                                    },
                                    {
                                      "snapshotDate": "2026-08-02",
                                      "items": [
                                        { "categoryId": 2, "amount": "2000.00" }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].snapshotDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].totalAmount").value("1000.00"))
                .andExpect(jsonPath("$.data[1].snapshotDate").value("2026-08-02"))
                .andExpect(jsonPath("$.data[1].totalAmount").value("2000.00"));

        ArgumentCaptor<List<AssetSnapshotBatchEntry>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotService).batchSave(captor.capture());

        List<AssetSnapshotBatchEntry> captured = captor.getValue();
        assertEquals(2, captured.size());
        assertEquals(LocalDate.of(2026, 8, 1), captured.get(0).snapshotDate());
        assertEquals(new BigDecimal("1000.00"),
                captured.get(0).items().get(0).getAmount());
        assertEquals(LocalDate.of(2026, 8, 2), captured.get(1).snapshotDate());
    }

    @Test
    void shouldReturn400WhenBodyIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/snapshots/batch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("批量快照数据不能为空"));
    }

    @Test
    void shouldReturn400WhenServiceRejectsEmptyEntries() throws Exception {
        when(assetSnapshotService.batchSave(any()))
                .thenThrow(new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        "批量快照数据不能为空"
                ));

        mockMvc.perform(post("/api/v1/snapshots/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "entries": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("批量快照数据不能为空"));
    }
}
