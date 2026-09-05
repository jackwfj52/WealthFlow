package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;

public interface SnapshotDraftService {

    CreateSnapshotDraftResponse createSnapshotDraft(
            CreateSnapshotDraftRequest request
    );
}