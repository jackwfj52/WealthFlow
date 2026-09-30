package org.jack.wealthflow.agent;

import java.util.regex.Pattern;

/** 直接执行时额外确认当前消息本身有明确的写入意图。 */
public final class AgentWriteIntent {
    private static final Pattern CREATE = Pattern.compile("新增|添加|创建|生成|补齐|补录|补上|录入|写入");
    private static final Pattern UPDATE = Pattern.compile(
            "修改|调整|增加|减少|调高|调低|更改|更正|改成|改为|设为|设置为|替换|覆盖|"
                    + "(?:添加|新增|删除|移除).{0,12}(?:分类|金额)"
    );
    private static final Pattern DELETE = Pattern.compile("删除|移除|清空|删掉");
    private static final Pattern HYPOTHETICAL = Pattern.compile(
            "如果|假如|假设|会怎样|会如何|会发生什么|仅分析|只分析"
    );
    private static final Pattern NEGATED_WRITE = Pattern.compile(
            "(?:不要|不需要|无需|别|先别).{0,8}(?:新增|添加|创建|生成|补齐|补录|修改|调整|更改|删除|移除|清空|执行)"
    );

    private AgentWriteIntent() {}

    public static boolean explicit(String message) {
        return message != null && (CREATE.matcher(message).find() || UPDATE.matcher(message).find()
                || DELETE.matcher(message).find())
                && !HYPOTHETICAL.matcher(message).find()
                && !NEGATED_WRITE.matcher(message).find();
    }

    public static boolean allows(String message, String operation) {
        if (!explicit(message)) return false;
        if (!message.contains("快照") && message.matches("(?s).*(?:新增|添加|创建|删除|移除).{0,8}分类.*")
                && (operation.equals("create") || operation.equals("delete"))) return false;
        return switch (operation) {
            case "create" -> CREATE.matcher(message).find();
            case "update" -> UPDATE.matcher(message).find();
            case "delete" -> DELETE.matcher(message).find();
            default -> false;
        };
    }
}
