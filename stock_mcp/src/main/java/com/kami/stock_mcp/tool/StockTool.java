package com.kami.stock_mcp.tool;

import com.kami.stock_mcp.entity.USStockRss;
import com.kami.stock_mcp.entity.vo.StockCountsVO;
import com.kami.stock_mcp.service.StockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 *
 * @Author kamiSheng
 * @Date 2026/10/5 17:09
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_mcp.tool
 */
@Slf4j
@Component
public class StockTool {
    @Resource
    private StockService stockService;
    @Tool(description = "根据股票id查询股票内容详情")
    public List<USStockRss> getStockByCode(String stockCode) {
        return stockService.getStockByCode(stockCode);
    }

    @Tool(description = "根据股票id和起始结束时间查询股票内容详情，时间格式为 yyyy-MM-dd HH:mm:ss，传入的为北京时间")
    public List<USStockRss> getStockByCodeBetweenData(String stockCode, String startTime, String endTime) {
        return stockService.getStockByCodeBetween(stockCode, startTime, endTime);
    }
    @Tool(description = "在起始结束时间内查询股票异动次数大于指定次数的股票，时间格式为 yyyy-MM-dd HH:mm:ss，传入的为北京时间")
    public List<StockCountsVO> queryStockBetweenData(String counts, String startTime, String endTime) {
        return stockService.queryStockBetweenData(counts, startTime, endTime);
    }
    @Tool(description = "查询股票标题包含指定关键词的股票")
    public List<USStockRss> queryStockByTitleKeyWords(String keyWords) {
        return stockService.queryStockByTitleKeyWords(keyWords);
    }
}
