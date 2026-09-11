package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.model.AssetSnapshotBatchEntry;
import org.jack.wealthflow.service.impl.AssetSnapshotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 deleteBySnapshotDate（Agent 删除草案执行时使用）的校验与删除行为。
 */
@ExtendWith(MockitoExtension.class)
class AssetSnapshotServiceTest {

    @Mock
    private AssetSnapshotMapper assetSnapshotMapper;

    @Mock
    private AssetCategoryMapper assetCategoryMapper;

    private AssetSnapshotService assetSnapshotService;

    @BeforeEach
    void setUp() {
        assetSnapshotService = new AssetSnapshotServiceImpl(
                assetSnapshotMapper,
                assetCategoryMapper
        );
    }

    private AssetSnapshot existingSnapshot(LocalDate date) {
        AssetSnapshot snapshot = new AssetSnapshot();
        snapshot.setId(100L);
        snapshot.setSnapshotDate(date);
        snapshot.setCategoryId(1L);
        snapshot.setAmount(new BigDecimal("5000.00"));
        return snapshot;
    }

    private AssetCategory category(Long id, String name) {
        AssetCategory category = new AssetCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private AssetSnapshot item(Long categoryId, String amount) {
        AssetSnapshot snapshot = new AssetSnapshot();
        snapshot.setCategoryId(categoryId);
        snapshot.setAmount(new BigDecimal(amount));
        return snapshot;
    }

    private AssetSnapshot detail(
            Long id,
            LocalDate date,
            Long categoryId,
            String amount
    ) {
        AssetSnapshot snapshot = new AssetSnapshot();
        snapshot.setId(id);
        snapshot.setSnapshotDate(date);
        snapshot.setCategoryId(categoryId);
        snapshot.setAmount(new BigDecimal(amount));
        return snapshot;
    }

    @Test
    void shouldBatchCreateWhenNoDateExists() {
        LocalDate date1 = LocalDate.of(2026, 8, 1);
        LocalDate date2 = LocalDate.of(2026, 8, 2);

        when(assetCategoryMapper.findById(1L)).thenReturn(category(1L, "现金"));
        when(assetCategoryMapper.findById(2L)).thenReturn(category(2L, "股票"));

        when(assetSnapshotMapper.findBySnapshotDate(date1)).thenReturn(List.of());
        when(assetSnapshotMapper.findBySnapshotDate(date2)).thenReturn(List.of());
        when(assetSnapshotMapper.insert(any(AssetSnapshot.class))).thenReturn(1);

        List<AssetSnapshotResponse> results = assetSnapshotService.batchSave(List.of(
                new AssetSnapshotBatchEntry(
                        date1,
                        List.of(item(1L, "1000.00"), item(2L, "2000.00"))
                ),
                new AssetSnapshotBatchEntry(
                        date2,
                        List.of(item(1L, "500.00"))
                )
        ));

        assertEquals(2, results.size());
        assertEquals(date1, results.get(0).getSnapshotDate());
        assertEquals(0, new BigDecimal("3000.00")
                .compareTo(results.get(0).getTotalAmount()));
        assertEquals(date2, results.get(1).getSnapshotDate());
        assertEquals(0, new BigDecimal("500.00")
                .compareTo(results.get(1).getTotalAmount()));
        verify(assetSnapshotMapper, times(3)).insert(any(AssetSnapshot.class));
    }

    @Test
    void shouldOverwriteExistingDateInBatch() {
        LocalDate date = LocalDate.of(2026, 8, 31);
        AssetSnapshot existing = existingSnapshot(date);

        when(assetCategoryMapper.findById(1L)).thenReturn(category(1L, "现金"));
        when(assetCategoryMapper.findById(2L)).thenReturn(category(2L, "股票"));

        when(assetSnapshotMapper.findBySnapshotDate(date))
                .thenReturn(List.of(existing))
                .thenReturn(List.of(
                        detail(100L, date, 1L, "6000.00"),
                        detail(101L, date, 2L, "7000.00")
                ));
        when(assetSnapshotMapper.update(any(AssetSnapshot.class))).thenReturn(1);
        when(assetSnapshotMapper.insert(any(AssetSnapshot.class))).thenReturn(1);

        List<AssetSnapshotResponse> results = assetSnapshotService.batchSave(List.of(
                new AssetSnapshotBatchEntry(
                        date,
                        List.of(item(1L, "6000.00"), item(2L, "7000.00"))
                )
        ));

        assertEquals(1, results.size());
        assertEquals(date, results.get(0).getSnapshotDate());
        assertEquals(0, new BigDecimal("13000.00")
                .compareTo(results.get(0).getTotalAmount()));
        verify(assetSnapshotMapper).update(any(AssetSnapshot.class));
        verify(assetSnapshotMapper).deleteBySnapshotDateExceptId(date, 100L);
        verify(assetSnapshotMapper).insert(any(AssetSnapshot.class));
    }

    @Test
    void shouldRejectNullBatchEntries() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.batchSave(null)
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectEmptyBatchEntries() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.batchSave(List.of())
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectDuplicateDatesInBatch() {
        LocalDate date = LocalDate.of(2026, 8, 1);

        when(assetCategoryMapper.findById(1L)).thenReturn(category(1L, "现金"));
        when(assetSnapshotMapper.findBySnapshotDate(date)).thenReturn(List.of());
        when(assetSnapshotMapper.insert(any(AssetSnapshot.class))).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.batchSave(List.of(
                        new AssetSnapshotBatchEntry(
                                date,
                                List.of(item(1L, "100.00"))
                        ),
                        new AssetSnapshotBatchEntry(
                                date,
                                List.of(item(1L, "200.00"))
                        )
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectTooManyBatchEntries() {
        List<AssetSnapshotBatchEntry> entries = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            entries.add(new AssetSnapshotBatchEntry(
                    LocalDate.of(2026, 1, 1),
                    List.of(item(1L, "100.00"))
            ));
        }

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.batchSave(entries)
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectFutureDateInBatch() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.batchSave(List.of(
                        new AssetSnapshotBatchEntry(
                                futureDate,
                                List.of(item(1L, "100.00"))
                        )
                ))
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    @Test
    void shouldRejectCreateWhenDateExists() {
        LocalDate date = LocalDate.of(2026, 8, 31);

        when(assetCategoryMapper.findById(1L)).thenReturn(category(1L, "现金"));
        when(assetSnapshotMapper.findBySnapshotDate(date))
                .thenReturn(List.of(existingSnapshot(date)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.create(
                        date,
                        List.of(item(1L, "100.00"))
                )
        );

        assertEquals(ErrorCode.SNAPSHOT_DATE_EXISTS, exception.getErrorCode());
        verify(assetSnapshotMapper, never()).insert(any(AssetSnapshot.class));
    }

    @Test
    void shouldUpdateSnapshot() {
        Long id = 100L;
        LocalDate date = LocalDate.of(2026, 8, 31);

        when(assetSnapshotMapper.findById(id))
                .thenReturn(existingSnapshot(date));
        when(assetCategoryMapper.findById(1L)).thenReturn(category(1L, "现金"));
        when(assetCategoryMapper.findById(2L)).thenReturn(category(2L, "股票"));
        when(assetSnapshotMapper.update(any(AssetSnapshot.class))).thenReturn(1);
        when(assetSnapshotMapper.insert(any(AssetSnapshot.class))).thenReturn(1);
        when(assetSnapshotMapper.findBySnapshotDate(date)).thenReturn(List.of(
                detail(100L, date, 1L, "6000.00"),
                detail(101L, date, 2L, "7000.00")
        ));

        AssetSnapshotResponse response = assetSnapshotService.update(
                id,
                List.of(item(1L, "6000.00"), item(2L, "7000.00"))
        );

        assertEquals(id, response.getId());
        assertEquals(date, response.getSnapshotDate());
        assertEquals(0, new BigDecimal("13000.00")
                .compareTo(response.getTotalAmount()));
    }

    @Test
    void shouldDeleteSnapshotsByDate() {
        LocalDate date = LocalDate.of(2026, 8, 31);

        when(assetSnapshotMapper.findBySnapshotDate(date))
                .thenReturn(List.of(existingSnapshot(date)));
        when(assetSnapshotMapper.deleteBySnapshotDate(date)).thenReturn(2);

        assetSnapshotService.deleteBySnapshotDate(date);

        verify(assetSnapshotMapper).deleteBySnapshotDate(date);
    }

    @Test
    void shouldRejectNullDate() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.deleteBySnapshotDate(null)
        );

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        verify(assetSnapshotMapper, never())
                .deleteBySnapshotDate(org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    void shouldRejectDateWithoutSnapshot() {
        LocalDate date = LocalDate.of(2026, 8, 31);

        when(assetSnapshotMapper.findBySnapshotDate(date))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.deleteBySnapshotDate(date)
        );

        assertEquals(ErrorCode.SNAPSHOT_NOT_FOUND, exception.getErrorCode());
        verify(assetSnapshotMapper, never()).deleteBySnapshotDate(date);
    }

    @Test
    void shouldThrowServerErrorWhenDeleteAffectsNoRows() {
        LocalDate date = LocalDate.of(2026, 8, 31);

        when(assetSnapshotMapper.findBySnapshotDate(date))
                .thenReturn(List.of(existingSnapshot(date)));
        when(assetSnapshotMapper.deleteBySnapshotDate(date)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetSnapshotService.deleteBySnapshotDate(date)
        );

        assertEquals(ErrorCode.SERVER_ERROR, exception.getErrorCode());
    }
}
