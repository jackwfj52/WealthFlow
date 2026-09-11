package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.CreateSnapshotDraftRequest;
import org.jack.wealthflow.dto.CreateSnapshotDraftResponse;
import org.jack.wealthflow.dto.DeleteSnapshotDraftRequest;
import org.jack.wealthflow.dto.DeleteSnapshotDraftResponse;

public interface SnapshotDraftService {

    CreateSnapshotDraftResponse createSnapshotDraft(
            CreateSnapshotDraftRequest request
    );

    DeleteSnapshotDraftResponse createDeleteSnapshotDraft(
            DeleteSnapshotDraftRequest request
    );
}