package org.jack.wealthflow.exception;

public enum ErrorCode {

    PARAM_INVALID(40001, 400, "请求参数不合法"),
    CATEGORY_NOT_FOUND(40401, 404, "该资产分类不存在"),
    SNAPSHOT_NOT_FOUND(40402, 404, "该资产快照不存在"),
    CATEGORY_NAME_EXISTS(40901, 409, "名称已存在"),
    CATEGORY_HAS_SNAPSHOTS(40902, 409, "资产分类下存在快照，无法删除"),
    SNAPSHOT_DATE_EXISTS(40903, 409, "该日期已经存在快照，请使用编辑功能"),
    SERVER_ERROR(50000, 500, "服务器内部错误");

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
