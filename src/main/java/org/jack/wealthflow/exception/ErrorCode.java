package org.jack.wealthflow.exception;

public enum ErrorCode {

    PARAM_INVALID(40001, 400, "请求参数不合法"),
    CATEGORY_NOT_FOUND(40401, 404, "该资产分类不存在"),
    SNAPSHOT_NOT_FOUND(40402, 404, "该资产快照不存在"),
    CATEGORY_NAME_EXISTS(40901, 409, "名称已存在"),
    CATEGORY_HAS_SNAPSHOTS(40902, 409, "资产分类下存在快照，无法删除"),
    SNAPSHOT_DATE_EXISTS(40903, 409, "该日期已经存在快照，请使用编辑功能"),

    PENDING_ACTION_NOT_FOUND(40403, 404, "待确认操作不存在"),
    PENDING_ACTION_NOT_PENDING(40904, 409, "该操作当前不能执行"),
    PENDING_ACTION_EXPIRED(40905, 409, "该操作已过期，请重新发起"),
    PENDING_ACTION_TYPE_MISMATCH(40906, 409, "待确认操作类型不匹配"),

    PROVIDER_CONFIG_NOT_FOUND(40404, 404, "AI 提供商配置不存在"),
    PROVIDER_ID_EXISTS(40907, 409, "该提供商已配置"),

    SERVER_ERROR(50000, 500, "服务器内部错误"),
    SNAPSHOT_DRAFT_PARSE_FAILED(50001, 500, "快照草案数据已损坏，无法执行"),
    DPAPI_UNAVAILABLE(50002, 500, "Windows 密钥保护不可用，无法安全保存 API Key"),
    DPAPI_ENCRYPT_FAILED(50003, 500, "API Key 加密失败，配置未保存"),
    DPAPI_DECRYPT_FAILED(50004, 500, "API Key 解密失败，无法读取该配置");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
