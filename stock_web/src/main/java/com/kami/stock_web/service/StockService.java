package com.kami.stock_web.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.kami.stock_web.entity.StockCounts;
import com.kami.stock_web.entity.USStockRss;

import java.time.LocalDateTime;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/19 15:41
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.service
 */
public interface StockService extends IService<USStockRss> {
    public StockCounts getStockCount(String stockCode, LocalDateTime start);
}
