package com.kami.stock_web.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.rometools.rome.feed.synd.SyndEntry;

import java.util.List;

public interface RssService {
    void displayRss() throws Exception;

    void displayRssZh() throws Exception;

    List<SyndEntry> fetchRssReed(String rssUrl) throws Exception;
}
