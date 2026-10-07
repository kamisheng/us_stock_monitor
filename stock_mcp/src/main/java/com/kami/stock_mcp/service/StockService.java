package com.kami.stock_mcp.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.kami.stock_mcp.entity.StockCounts;
import com.kami.stock_mcp.entity.USStockRss;
import com.kami.stock_mcp.entity.vo.StockCountsVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/19 15:41
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.service
 */
public interface StockService extends IService<USStockRss> {
    List<USStockRss> getStockByCode(String stockCode);
    List<USStockRss> getStockByCodeBetween(String stockCode, String startTime, String endTime);

    /**
     * 在起始结束时间内查询股票异动次数大于指定次数的股票
     * @param counts
     * @param startTime
     * @param endTime
     * @return
     */
    List<StockCountsVO> queryStockBetweenData(String counts, String startTime, String endTime);
    List<USStockRss> queryStockByTitleKeyWords(String keyWords);
}
