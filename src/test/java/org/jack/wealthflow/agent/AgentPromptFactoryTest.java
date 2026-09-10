package org.jack.wealthflow.agent;

import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPromptFactoryTest {

    @Mock
    private AssetCategoryMapper assetCategoryMapper;

    @Mock
    private AssetSnapshotMapper assetSnapshotMapper;

    private AgentPromptFactory promptFactory;

    @BeforeEach
    void setUp() {
        promptFactory = new AgentPromptFactory(
                assetCategoryMapper,
                assetSnapshotMapper
        );
    }

    private AssetCategory category(long id, String name) {
        AssetCategory category = new AssetCategory();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private AssetSnapshot snapshot(
            LocalDate date,
            long categoryId,
            String amount
    ) {
        AssetSnapshot snapshot = new AssetSnapshot();
        snapshot.setSnapshotDate(date);
        snapshot.setCategoryId(categoryId);
        snapshot.setAmount(new BigDecimal(amount));
        return snapshot;
    }

    @Test
    void shouldBuildPromptWithCategoriesAndSnapshots() {
        when(assetCategoryMapper.findAll()).thenReturn(List.of(
                category(1L, "现金"),
                category(2L, "基金")
        ));
        when(assetSnapshotMapper.findAll()).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 9, 1), 1L, "5000.00"),
                snapshot(LocalDate.of(2026, 9, 1), 2L, "12000.00"),
                snapshot(LocalDate.of(2026, 8, 1), 1L, "4000.00"),
                snapshot(LocalDate.of(2026, 8, 1), 2L, "11000.00")
        ));

        String prompt = promptFactory.buildSystemPrompt();

        assertTrue(prompt.contains("你是 WealthFlow 的中文资产助手。"));
        assertTrue(prompt.contains("本地资产上下文"));
        assertTrue(prompt.contains("ID 1：现金"));
        assertTrue(prompt.contains("ID 2：基金"));
        assertTrue(prompt.contains("最近快照：2026-09-01"));
        assertTrue(prompt.contains("现金：5000.00 元"));
        assertTrue(prompt.contains("基金：12000.00 元"));
        assertTrue(prompt.contains("合计：17000.00 元"));
        assertTrue(prompt.contains("- 2026-09-01：17000.00 元"));
        assertTrue(prompt.contains("- 2026-08-01：15000.00 元"));
    }

    @Test
    void shouldBuildPromptWithEmptyDataHints() {
        when(assetCategoryMapper.findAll()).thenReturn(List.of());
        when(assetSnapshotMapper.findAll()).thenReturn(List.of());

        String prompt = promptFactory.buildSystemPrompt();

        assertTrue(prompt.contains("（暂无分类）"));
        assertTrue(prompt.contains("最近快照：（暂无快照）"));
        assertTrue(prompt.contains("（暂无历史快照）"));
    }

    @Test
    void shouldNotExposeInternalPathsOrSecrets() {
        when(assetCategoryMapper.findAll()).thenReturn(List.of(
                category(1L, "现金")
        ));
        when(assetSnapshotMapper.findAll()).thenReturn(List.of(
                snapshot(LocalDate.of(2026, 9, 1), 1L, "5000.00")
        ));

        String prompt = promptFactory.buildSystemPrompt();

        assertFalse(prompt.contains("wealthflow.db"));
        assertFalse(prompt.contains("sqlite"));
        assertFalse(prompt.contains("jdbc"));
        assertFalse(prompt.contains("C:"));
        assertFalse(prompt.contains("apiKey"));
    }

    @Test
    void shouldCapHistoryEntriesAt20() {
        when(assetCategoryMapper.findAll()).thenReturn(List.of(
                category(1L, "现金")
        ));

        List<AssetSnapshot> snapshots = new ArrayList<>();
        for (int daysAgo = 0; daysAgo < 25; daysAgo++) {
            snapshots.add(snapshot(
                    LocalDate.of(2026, 9, 10).minusDays(daysAgo),
                    1L,
                    "1000.00"
            ));
        }
        when(assetSnapshotMapper.findAll()).thenReturn(snapshots);

        String prompt = promptFactory.buildSystemPrompt();

        // 最新的 20 个日期保留，最旧的 5 个被裁剪
        assertTrue(prompt.contains(
                "- " + LocalDate.of(2026, 9, 10).minusDays(19) + "：1000.00 元"
        ));
        assertFalse(prompt.contains(
                "- " + LocalDate.of(2026, 9, 10).minusDays(20) + "：1000.00 元"
        ));
    }
}
