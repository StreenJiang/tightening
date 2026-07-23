package com.tightening.mapper;

import com.tightening.entity.ProductSide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ProductSideMapper extends BaseMapper<ProductSide> {

    @Select("""
        <script>
        SELECT id, product_task_id, thumbnail_data
        FROM product_side ps
        WHERE ps.product_task_id IN
            <foreach collection="taskIds" item="id" open="(" separator="," close=")">#{id}</foreach>
          AND ps.deleted = 0
          AND ps.id = (SELECT MIN(ps2.id) FROM product_side ps2
                       WHERE ps2.product_task_id = ps.product_task_id
                         AND ps2.deleted = 0)
        </script>
        """)
    @ResultMap("mybatis-plus_ProductSide")
    List<ProductSide> selectFirstSidePerTask(List<Long> taskIds);
}
