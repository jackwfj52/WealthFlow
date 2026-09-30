package org.jack.wealthflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiPermissionMapper {
    String findLevel();

    int updateLevel(@Param("level") String level);
}
