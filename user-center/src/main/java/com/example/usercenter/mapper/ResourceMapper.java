package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 资源Mapper接口
 * @author zy
 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    /**
     * 原子递增下载次数（避免并发丢失更新）
     */
    @Update("UPDATE resource SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);
}
