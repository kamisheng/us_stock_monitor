package com.kami.stock_web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kami.stock_web.entity.StockCounts;
import com.kami.stock_web.entity.USStockRss;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

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
}
