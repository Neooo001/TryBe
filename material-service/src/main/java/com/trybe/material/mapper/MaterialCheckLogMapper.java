package com.trybe.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trybe.material.domain.po.MaterialCheckLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 素材校验日志表 Mapper 接口
 * </p>
 */
public interface MaterialCheckLogMapper extends BaseMapper<MaterialCheckLog> {

    /**
     * 物理删除指定素材的所有校验日志
     */
    @Delete("DELETE FROM material_check_log WHERE material_id = #{materialId}")
    int deleteByMaterialId(@Param("materialId") Long materialId);
}
