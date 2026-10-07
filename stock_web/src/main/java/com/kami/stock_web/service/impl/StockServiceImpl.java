package com.kami.stock_web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.kami.stock_web.entity.StockCounts;
import com.kami.stock_web.entity.USStockRss;
import com.kami.stock_web.mapper.USStockRssMapper;
import com.kami.stock_web.service.StockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.kami.stock_web.utils.GMTDateConverter.*;

/**
 *
 * @Author kamiSheng
 * @Date 2026/9/19 15:42
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_web.service.impl
 */
@Service
public class StockServiceImpl extends ServiceImpl<USStockRssMapper, USStockRss> implements StockService {
    @Resource
    private USStockRssMapper usStockRssMapper;
    @Override
    public StockCounts getStockCount(String stockCode, LocalDateTime time) {
        return usStockRssMapper.selectCounts(stockCode, minus24Hour(time), minus3Day(time), minus1Week(time), plus1Minute(time));
    }

}
