package org.jack.wealthflow.constant;

public class MessageConstant {


    public static final String ASSET_CATEGORY_ADD_FAILED = "资产分类新增失败";
    public static final String COLOR_INVALID = "颜色格式无效，需为 #RRGGBB 格式";
    public static final String ID_NOT_EMPTY = "ID不能为空";
    public static final String NAME_NOT_EMPTY = "名称不能为空";
    public static final String NAME_NOT_TOO_LONG = "名称不能超过20个字符";
    public static final String NAME_ALREADY_EXISTS = "名称已存在";
    public static final String ASSET_CATEGORY_NOT_FOUND = "该资产分类不存在";
    public static final String ASSET_CATEGORY_UPDATE_FAILED = "资产分类修改失败";
    public static final String ASSET_CATEGORY_DELETE_FAILED = "资产分类删除失败";
    public static final String ASSET_CATEGORY_HAS_SNAPSHOTS = "资产分类下存在快照，无法删除";

    public static final String SNAPSHOT_NOT_FOUND = "该资产快照不存在";
    public static final String SNAPSHOT_DATE_EXISTS = "该日期已经存在快照，请使用编辑功能";
    public static final String SNAPSHOT_DATE_NOT_EMPTY = "快照日期不能为空";
    public static final String SNAPSHOT_DATE_CANNOT_BE_FUTURE = "快照日期不能晚于今天";
    public static final String SNAPSHOT_ITEMS_NOT_EMPTY = "快照明细不能为空";
    public static final String SNAPSHOT_CATEGORY_ID_NOT_EMPTY = "快照明细的资产分类ID不能为空";
    public static final String SNAPSHOT_AMOUNT_INVALID = "快照金额必须大于0";
    public static final String SNAPSHOT_CATEGORY_DUPLICATE = "同一快照中不能包含重复的资产分类";
    public static final String ASSET_SNAPSHOT_ADD_FAILED = "资产快照新增失败";
    public static final String ASSET_SNAPSHOT_UPDATE_FAILED = "资产快照修改失败";
    public static final String ASSET_SNAPSHOT_DELETE_FAILED = "资产快照删除失败";

    public static final String PENDING_ACTION_NOT_FOUND = "待确认操作不存在";
    public static final String PENDING_ACTION_NOT_PENDING = "该操作当前不能执行";
    public static final String PENDING_ACTION_EXPIRED = "该操作已过期，请重新发起";
    public static final String PENDING_ACTION_CREATE_FAILED = "待确认操作创建失败";
    public static final String PENDING_ACTION_STATUS_UPDATE_FAILED = "待确认操作状态更新失败";
    public static final String PENDING_ACTION_TYPE_MISMATCH = "待确认操作类型不匹配";
    public static final String SNAPSHOT_DRAFT_SERIALIZE_FAILED = "快照草案数据处理失败";
    public static final String SNAPSHOT_DRAFT_PARSE_FAILED = "快照草案数据已损坏，无法执行";

    public static final String AI_PROVIDER_NOT_FOUND = "AI 提供商配置不存在";
    public static final String AI_PROVIDER_ID_EXISTS = "该提供商已配置";
    public static final String AI_PROVIDER_ID_NOT_EMPTY = "提供商ID不能为空";
    public static final String AI_PROVIDER_DISPLAY_NAME_NOT_EMPTY = "显示名称不能为空";
    public static final String AI_PROVIDER_BASE_URL_NOT_EMPTY = "Base URL不能为空";
    public static final String AI_PROVIDER_BASE_URL_INVALID = "Base URL 必须以 http:// 或 https:// 开头";
    public static final String AI_PROVIDER_MODEL_NOT_EMPTY = "模型名称不能为空";
    public static final String AI_PROVIDER_API_KEY_NOT_EMPTY = "API Key不能为空";
    public static final String AI_PROVIDER_PROTOCOL_UNSUPPORTED = "暂不支持该协议，仅支持 OPENAI_COMPATIBLE";
    public static final String AI_PROVIDER_SAVE_FAILED = "AI 提供商配置保存失败";
    public static final String AI_PROVIDER_UPDATE_FAILED = "AI 提供商配置更新失败";
    public static final String AI_PROVIDER_DELETE_FAILED = "AI 提供商配置删除失败";
    public static final String DPAPI_UNAVAILABLE = "Windows 密钥保护不可用，无法安全保存 API Key";
    public static final String DPAPI_ENCRYPT_FAILED = "API Key 加密失败，配置未保存";
    public static final String DPAPI_DECRYPT_FAILED = "API Key 解密失败，无法读取该配置";
}
