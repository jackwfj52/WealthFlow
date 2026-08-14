package org.jack.wealthflow.service;

import org.jack.wealthflow.dto.SystemInfoResponse;

public interface SystemService {

    /** 数据库信息：文件路径与数据量统计 */
    SystemInfoResponse getInfo();

    /** 清空全部数据（快照明细 + 分类），不可恢复 */
    void clearAll();
}
