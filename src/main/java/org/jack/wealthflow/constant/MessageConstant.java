package org.jack.wealthflow.constant;

public class MessageConstant {


    public static final String ASSET_CATEGORY_ADD_FAILED = "资产分类新增失败";
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

}
