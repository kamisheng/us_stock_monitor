package com.kami.stock_mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kami.stock_mcp.entity.StockCounts;
import com.kami.stock_mcp.entity.USStockRss;
import com.kami.stock_mcp.entity.dto.QueryCountsDTO;
import com.kami.stock_mcp.entity.vo.StockCountsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface USStockRssMapper extends BaseMapper<USStockRss> {
    @Select("""
SELECT
  COALESCE(SUM(pub_date_gmt BETWEEN #{h24From} AND #{to}), 0) AS counts24Hour,
  COALESCE(SUM(pub_date_gmt BETWEEN #{d3From}  AND #{to}), 0) AS counts3Day,
  COALESCE(SUM(pub_date_gmt BETWEEN #{w1From}  AND #{to}), 0) AS counts1Week
FROM us_stock_rss
WHERE stock_code = #{stockCode}
  AND pub_date_gmt BETWEEN #{w1From} AND #{to}
""")
    StockCounts selectCounts(@Param("stockCode") String stockCode,
                             @Param("h24From") LocalDateTime h24From,

                             @Param("d3From")  LocalDateTime d3From,
                             @Param("w1From")  LocalDateTime w1From,
                             @Param("to") LocalDateTime to);
    /**
     * 查询指定时间区间内异动次数 >= counts 的股票。
     * <p>
     * 注意：方法只有一个参数，必须加 {@code @Param} 才能在 SQL 里用
     * {@code #{queryCountsDTO.xxx}} 这种带前缀的写法；否则 MyBatis 会把 DTO 本身当参数对象，
     * 去 DTO 上找 getQueryCountsDTO() 而抛
     * {@code ReflectionException: There is no getter for property named 'queryCountsDTO'}。
     * 不加 @Param 的话，SQL 里要写裸属性名 {@code #{startTime}}。
     */
    @Select("SELECT stock_code AS stockCode, COUNT(*) AS cnt" +
            " FROM us_stock_rss" +
            " WHERE pub_date_bj >= #{queryCountsDTO.startTime}" +
            " AND pub_date_bj <= #{queryCountsDTO.endTime}" +
            " GROUP BY stock_code" +
            " HAVING cnt >= #{queryCountsDTO.counts}" +
            " ORDER BY cnt DESC")
    List<StockCountsVO> queryCountsBetweenData(@Param("queryCountsDTO") QueryCountsDTO queryCountsDTO);
    @Select("SELECT *" +
            " FROM us_stock_rss" +
            " WHERE (title LIKE CONCAT('%', #{keyWords}, '%')" +
            " OR title_zh LIKE CONCAT('%', #{keyWords}, '%')" +
            " OR content LIKE CONCAT('%', #{keyWords}, '%'))" +
            " ORDER BY pub_date_bj DESC")
    List<USStockRss> queryStockByTitleKeyWords(@Param("keyWords") String keyWords);

}
