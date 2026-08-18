package com.trybe.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trybe.material.domain.po.Material;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 素材表 Mapper 接口
 * </p>
 */
public interface MaterialMapper extends BaseMapper<Material> {

    /**
     * 物理删除素材（绕过 @TableLogic 逻辑删除）
     */
    @Delete("DELETE FROM material WHERE id = #{id}")
    int physicallyDeleteById(@Param("id") Long id);

    /**
     * 查询已软删除且早于指定时间的素材（绕过 @TableLogic 过滤）
     */
    @Select("SELECT * FROM material WHERE deleted = 1 AND update_time < #{beforeTime} LIMIT 500")
    List<Material> selectDeletedBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
