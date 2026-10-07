package com.kami.stock_mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.kami.stock_mcp.entity.StockCounts;
import com.kami.stock_mcp.entity.USStockRss;
import com.kami.stock_mcp.entity.dto.QueryCountsDTO;
import com.kami.stock_mcp.entity.vo.StockCountsVO;
import com.kami.stock_mcp.mapper.USStockRssMapper;
import com.kami.stock_mcp.service.StockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.kami.stock_mcp.utils.GMTDateConverter.*;

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
    public List<USStockRss> getStockByCode(String stockCode) {
        LambdaQueryWrapper<USStockRss> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(USStockRss::getStockCode, stockCode);
        List<USStockRss> usStockRsses = usStockRssMapper.selectList(queryWrapper);
        if(usStockRsses == null)
            return List.of();
        return usStockRsses;
    }

    @Override
    public List<USStockRss> getStockByCodeBetween(String stockCode, String startTime, String endTime) {
        LambdaQueryWrapper<USStockRss> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(USStockRss::getStockCode, stockCode)
                .between(USStockRss::getPubDateBj, startTime, endTime)
                .orderByDesc(USStockRss::getPubDateBj);
        List<USStockRss> usStockRsses = usStockRssMapper.selectList(queryWrapper);
        if(usStockRsses == null)
            return List.of();
        return usStockRsses;
    }

    @Override
    public List<StockCountsVO> queryStockBetweenData(String counts, String startTime, String endTime) {
        List<StockCountsVO> stockCountsVOS = usStockRssMapper.queryCountsBetweenData(new QueryCountsDTO(counts, startTime, endTime));
        if(stockCountsVOS == null || stockCountsVOS.isEmpty()) return List.of();
        return stockCountsVOS;
    }

    @Override
    public List<USStockRss> queryStockByTitleKeyWords(String keyWords) {
        List<USStockRss> usStockRsses = usStockRssMapper.queryStockByTitleKeyWords(keyWords);
        if(usStockRsses == null || usStockRsses.isEmpty()) return List.of();
        return usStockRsses;
    }
}
