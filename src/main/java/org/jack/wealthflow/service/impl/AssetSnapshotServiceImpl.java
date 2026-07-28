package org.jack.wealthflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.dto.AssetSnapshotResponse;
import org.jack.wealthflow.dto.SnapshotItem;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.jack.wealthflow.mapper.AssetCategoryMapper;
import org.jack.wealthflow.mapper.AssetSnapshotMapper;
import org.jack.wealthflow.model.AssetCategory;
import org.jack.wealthflow.model.AssetSnapshot;
import org.jack.wealthflow.service.AssetSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetSnapshotServiceImpl implements AssetSnapshotService {

    private final AssetSnapshotMapper assetSnapshotMapper;
    private final AssetCategoryMapper assetCategoryMapper;

    @Override
    public List<AssetSnapshotResponse> findAll() {
        List<AssetSnapshot> details = assetSnapshotMapper.findAll();
        Map<LocalDate, List<AssetSnapshot>> detailsByDate = details.stream()
                .collect(Collectors.groupingBy(
                        AssetSnapshot::getSnapshotDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Long, String> categoryNames = new HashMap<>();
        List<AssetSnapshotResponse> responses = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AssetSnapshot>> entry : detailsByDate.entrySet()) {
            List<AssetSnapshot> snapshotDetails = entry.getValue();
            responses.add(toResponse(
                    snapshotDetails.get(0).getId(),
                    entry.getKey(),
                    snapshotDetails,
                    categoryNames
            ));
        }
        return responses;
    }

    @Override
    public AssetSnapshotResponse findById(Long id) {
        AssetSnapshot existing = requireSnapshot(id);
        List<AssetSnapshot> details =
                assetSnapshotMapper.findBySnapshotDate(existing.getSnapshotDate());

        return toResponse(
                existing.getId(),
                existing.getSnapshotDate(),
                details,
                new HashMap<>()
        );
    }

    @Override
    @Transactional
    public AssetSnapshotResponse create(LocalDate snapshotDate, List<AssetSnapshot> items) {
        validateSnapshotDate(snapshotDate);
        List<AssetSnapshot> validItems = validateAndCopyItems(items);

        if (!assetSnapshotMapper.findBySnapshotDate(snapshotDate).isEmpty()) {
            throw new BusinessException(
                    ErrorCode.SNAPSHOT_DATE_EXISTS,
                    MessageConstant.SNAPSHOT_DATE_EXISTS
            );
        }

        for (AssetSnapshot item : validItems) {
            item.setSnapshotDate(snapshotDate);
            int rows = assetSnapshotMapper.insert(item);
            if (rows != 1) {
                throw new BusinessException(
                        ErrorCode.SERVER_ERROR,
                        MessageConstant.ASSET_SNAPSHOT_ADD_FAILED
                );
            }
        }

        return toResponse(
                validItems.get(0).getId(),
                snapshotDate,
                validItems,
                new HashMap<>()
        );
    }

    @Override
    @Transactional
    public AssetSnapshotResponse update(Long id, List<AssetSnapshot> items) {
        AssetSnapshot existing = requireSnapshot(id);
        List<AssetSnapshot> validItems = validateAndCopyItems(items);

        AssetSnapshot retainedItem = validItems.get(0);
        retainedItem.setId(existing.getId());
        retainedItem.setSnapshotDate(existing.getSnapshotDate());

        if (assetSnapshotMapper.update(retainedItem) != 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.ASSET_SNAPSHOT_UPDATE_FAILED
            );
        }

        assetSnapshotMapper.deleteBySnapshotDateExceptId(
                existing.getSnapshotDate(),
                existing.getId()
        );

        for (int index = 1; index < validItems.size(); index++) {
            AssetSnapshot item = validItems.get(index);
            item.setSnapshotDate(existing.getSnapshotDate());

            if (assetSnapshotMapper.insert(item) != 1) {
                throw new BusinessException(
                        ErrorCode.SERVER_ERROR,
                        MessageConstant.ASSET_SNAPSHOT_UPDATE_FAILED
                );
            }
        }

        List<AssetSnapshot> updatedDetails =
                assetSnapshotMapper.findBySnapshotDate(existing.getSnapshotDate());
        return toResponse(
                existing.getId(),
                existing.getSnapshotDate(),
                updatedDetails,
                new HashMap<>()
        );
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        AssetSnapshot existing = requireSnapshot(id);
        int rows = assetSnapshotMapper.deleteBySnapshotDate(existing.getSnapshotDate());

        if (rows < 1) {
            throw new BusinessException(
                    ErrorCode.SERVER_ERROR,
                    MessageConstant.ASSET_SNAPSHOT_DELETE_FAILED
            );
        }
    }

    private AssetSnapshot requireSnapshot(Long id) {
        if (id == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.ID_NOT_EMPTY
            );
        }

        AssetSnapshot snapshot = assetSnapshotMapper.findById(id);
        if (snapshot == null) {
            throw new BusinessException(
                    ErrorCode.SNAPSHOT_NOT_FOUND,
                    MessageConstant.SNAPSHOT_NOT_FOUND
            );
        }
        return snapshot;
    }

    private void validateSnapshotDate(LocalDate snapshotDate) {
        if (snapshotDate == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DATE_NOT_EMPTY
            );
        }

        if (snapshotDate.isAfter(LocalDate.now())) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_DATE_CANNOT_BE_FUTURE
            );
        }
    }

    private List<AssetSnapshot> validateAndCopyItems(List<AssetSnapshot> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    MessageConstant.SNAPSHOT_ITEMS_NOT_EMPTY
            );
        }

        Set<Long> categoryIds = new HashSet<>();
        List<AssetSnapshot> copiedItems = new ArrayList<>();

        for (AssetSnapshot item : items) {
            if (item == null || item.getCategoryId() == null) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_CATEGORY_ID_NOT_EMPTY
                );
            }

            if (!categoryIds.add(item.getCategoryId())) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_CATEGORY_DUPLICATE
                );
            }

            if (item.getAmount() == null
                    || item.getAmount().compareTo(BigDecimal.ZERO) <= 0
                    || item.getAmount().scale() > 2) {
                throw new BusinessException(
                        ErrorCode.PARAM_INVALID,
                        MessageConstant.SNAPSHOT_AMOUNT_INVALID
                );
            }

            if (assetCategoryMapper.findById(item.getCategoryId()) == null) {
                throw new BusinessException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        MessageConstant.ASSET_CATEGORY_NOT_FOUND
                );
            }

            AssetSnapshot copiedItem = new AssetSnapshot();
            copiedItem.setCategoryId(item.getCategoryId());
            copiedItem.setAmount(item.getAmount());
            copiedItems.add(copiedItem);
        }

        copiedItems.sort(Comparator.comparing(AssetSnapshot::getCategoryId));
        return copiedItems;
    }

    private AssetSnapshotResponse toResponse(
            Long id,
            LocalDate snapshotDate,
            List<AssetSnapshot> details,
            Map<Long, String> categoryNames
    ) {
        List<SnapshotItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (AssetSnapshot detail : details) {
            SnapshotItem item = new SnapshotItem();
            item.setCategoryId(detail.getCategoryId());
            item.setCategoryName(
                    categoryNames.computeIfAbsent(
                            detail.getCategoryId(),
                            this::findCategoryName
                    )
            );
            item.setAmount(detail.getAmount());
            items.add(item);
            totalAmount = totalAmount.add(detail.getAmount());
        }

        AssetSnapshotResponse response = new AssetSnapshotResponse();
        response.setId(id);
        response.setSnapshotDate(snapshotDate);
        response.setItems(items);
        response.setTotalAmount(totalAmount);
        return response;
    }

    private String findCategoryName(Long categoryId) {
        AssetCategory category = assetCategoryMapper.findById(categoryId);
        if (category == null) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NOT_FOUND,
                    MessageConstant.ASSET_CATEGORY_NOT_FOUND
            );
        }
        return category.getName();
    }
}
