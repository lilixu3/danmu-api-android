import BaseSource from './base.js';
import { globals } from '../configs/globals.js';
import { log } from "../utils/log-util.js";
import { httpGet } from "../utils/http-util.js";
import { convertToAsciiSum } from "../utils/codec-util.js";
import { generateValidStartDate } from "../utils/time-util.js";
import { addAnime, removeEarliestAnime } from "../utils/cache-util.js";
import { titleMatches } from "../utils/common-util.js";

// =====================
// 获取搜狐视频弹幕
// =====================
export default class SohuSource extends BaseSource {
  constructor() {
    super();
    this.danmuApiUrl = "https://api.danmu.tv.sohu.com/dmh5/dmListAll";
    this.searchApiUrl = "https://m.so.tv.sohu.com/search/pc/keyword";
    this.playlistApiUrl = "https://pl.hd.sohu.com/videolist";
    this.apiKey = "f351515304020cad28c92f70f002261c";
    this.episodesCache = new Map(); // 缓存分集列表
  }

  /**
   * 过滤搜狐视频搜索项
   */
  filterSohuSearchItem(item, keyword) {
    // 只处理剧集类型 (data_type=257)
    if (item.data_type !== 257) {
      return null;
    }

    if (!item.aid || !item.album_name) {
      return null;
    }

    // 清理标题中的高亮标记
    let title = item.album_name.replace(/<<<|>>>/g, '');

    // 从meta中提取类型信息
    let categoryName = null;
    if (item.meta && item.meta.length >= 2) {
      const metaText = item.meta[1].txt; 
      const parts = metaText.split('|');
      if (parts.length > 0) {
        categoryName = parts[0].trim();
      }
    }

    // 映射类型
    let type = this.mapCategoryToType(categoryName);

    // 过滤掉不支持的类型
    if (!type) {
      return null;
    }

    // 缓存分集列表
    if (item.videos && item.videos.length > 0) {
      this.episodesCache.set(String(item.aid), item.videos);
      log("debug", `[Sohu] 缓存了 ${item.videos.length} 个分集 (aid=${item.aid})`);
    }

    return {
      provider: "sohu",
      mediaId: String(item.aid),
      title: title,
      type: type,
      year: item.year || 0,
      imageUrl: item.ver_big_pic || "",
      episodeCount: item.total_video_count || 0,
      videos: item.videos || [] 
    };
  }

  /**
   * 类型映射
   */
  mapCategoryToType(categoryName) {
    if (!categoryName) {
      return null;
    }

    const categoryLower = categoryName.toLowerCase();
    const typeMap = {
      '电影': '电影',
      '电视剧': '电视剧',
      '动漫': '动漫',
      '纪录片': '纪录片',
      '综艺': '综艺',
      '综艺节目': '综艺'
    };

    for (const [key, value] of Object.entries(typeMap)) {
      if (categoryLower.includes(key.toLowerCase()) || categoryName.includes(key)) {
        return value;
      }
    }
    return null;
  }

  async search(keyword) {
    try {
      log("info", `[Sohu] 开始搜索: ${keyword}`);

      const params = new URLSearchParams({
        key: keyword,
        type: '1',
        page: '1',
        page_size: '20',
        user_id: '',
        tabsChosen: '0',
        poster: '4',
        tuple: '6',
        extSource: '1',
        show_star_detail: '3',
        pay: '1',
        hl: '3',
        uid: String(Date.now()),
        passport: '',
        plat: '-1',
        ssl: '0'
      });

      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
        'Referer': 'https://so.tv.sohu.com/',
        'Origin': 'https://so.tv.sohu.com'
      };

      const response = await httpGet(`${this.searchApiUrl}?${params.toString()}`, { headers });

      if (!response || !response.data) {
        log("info", "[Sohu] 搜索响应为空");
        return [];
      }

      const data = typeof response.data === "string" ? JSON.parse(response.data) : response.data;

      if (!data.data || !data.data.items || data.data.items.length === 0) {
        log("info", `[Sohu] 搜索 '${keyword}' 未找到结果`);
        return [];
      }

      const results = [];
      for (const item of data.data.items) {
        const filtered = this.filterSohuSearchItem(item, keyword);
        if (filtered) {
          results.push(filtered);
        }
      }

      log("info", `[Sohu] 搜索找到 ${results.length} 个有效结果`);
      return results;

    } catch (error) {
      log("error", "[Sohu] 搜索出错:", error.message);
      return [];
    }
  }

  async getEpisodes(mediaId) {
    try {
      log("info", `[Sohu] 获取分集列表: aid=${mediaId}`);

      let videosData = this.episodesCache.get(mediaId);

      if (!videosData) {
        log("info", `[Sohu] 缓存未命中，调用播放列表API (aid=${mediaId})`);

        const params = new URLSearchParams({
          playlistid: mediaId,
          api_key: this.apiKey
        });

        const headers = {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'Referer': 'https://tv.sohu.com/'
        };

        const response = await httpGet(`${this.playlistApiUrl}?${params.toString()}`, { headers });

        if (!response || !response.data) {
          log("error", "[Sohu] 获取分集列表响应为空");
          return [];
        }

        let text = response.data;
        if (typeof text !== 'string') {
          text = JSON.stringify(text);
        }

        let data;
        if (text.startsWith('jsonp')) {
          const start = text.indexOf('(') + 1;
          const end = text.lastIndexOf(')');
          if (start > 0 && end > start) {
            const jsonStr = text.substring(start, end);
            data = JSON.parse(jsonStr);
          } else {
            return [];
          }
        } else {
          data = typeof text === 'string' ? JSON.parse(text) : text;
        }

        videosData = data.videos || [];
      }

      if (!videosData || videosData.length === 0) {
        log("warn", `[Sohu] 未找到分集列表 (aid=${mediaId})`);
        return [];
      }

      const episodes = [];
      for (let i = 0; i < videosData.length; i++) {
        const video = videosData[i];
        const vid = String(video.vid);
        const title = video.video_name || video.name || `第${i + 1}集`;
        let url = video.url_html5 || video.pageUrl || '';

        if (url.startsWith('http://')) {
          url = url.replace('http://', 'https://');
        }

        episodes.push({
          vid: vid,
          title: title,
          episodeId: `${vid}:${mediaId}`,
          url: url
        });
      }

      log("info", `[Sohu] 成功获取 ${episodes.length} 个分集 (aid=${mediaId})`);
      return episodes;

    } catch (error) {
      log("error", "[Sohu] 获取分集列表出错:", error.message);
      return [];
    }
  }

  async handleAnimes(sourceAnimes, queryTitle, curAnimes) {
    const tmpAnimes = [];

    if (!sourceAnimes || !Array.isArray(sourceAnimes)) {
      log("error", "[Sohu] sourceAnimes is not a valid array");
      return [];
    }

    const processSohuAnimes = await Promise.all(sourceAnimes
      .filter(s => titleMatches(s.title, queryTitle))
      .map(async (anime) => {
        try {
          const eps = await this.getEpisodes(anime.mediaId);
          let links = [];

          const numericAnimeId = convertToAsciiSum(anime.mediaId);

          for (let i = 0; i < eps.length; i++) {
            const ep = eps[i];
            const epTitle = ep.title || `第${i + 1}集`;
            const fullUrl = ep.url || `https://tv.sohu.com/item/${anime.mediaId}.html`;

            const episodeNumericId = numericAnimeId * 1000000 + (i + 1);

            links.push({
              "name": (i + 1).toString(),
              "url": fullUrl,
              "title": `【sohu】 ${epTitle}`,
              "id": episodeNumericId
            });
          }

          if (links.length > 0) {
            let transformedAnime = {
              animeId: numericAnimeId,
              bangumiId: anime.mediaId,
              animeTitle: `${anime.title}(${anime.year})【${anime.type}】from sohu`,
              type: anime.type,
              typeDescription: anime.type,
              imageUrl: anime.imageUrl,
              startDate: generateValidStartDate(anime.year),
              episodeCount: links.length,
              rating: 0,
              isFavorited: true,
              source: "sohu",
            };

            tmpAnimes.push(transformedAnime);
            addAnime({...transformedAnime, links: links});

            if (globals.animes.length > globals.MAX_ANIMES) removeEarliestAnime();
          }
        } catch (error) {
          log("error", `[Sohu] Error processing anime: ${error.message}`);
        }
      })
    );

    this.sortAndPushAnimesByYear(tmpAnimes, curAnimes);
    return processSohuAnimes;
  }

  /**
   * 获取某集的弹幕（原始数据）
   */
  async getEpisodeDanmu(url) {
    log("info", "[Sohu] 开始从本地请求搜狐视频弹幕...", url);

    try {
      let vid, aid;

      // 1. 处理完整 URL
      if (url.includes('tv.sohu.com')) {
        const pageResponse = await httpGet(url, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'https://tv.sohu.com/'
          }
        });

        if (!pageResponse || !pageResponse.data) {
          log("error", "[Sohu] 无法获取页面内容");
          return [];
        }

        const pageContent = typeof pageResponse.data === 'string' 
          ? pageResponse.data 
          : JSON.stringify(pageResponse.data);

        // 从页面中提取vid和aid
        const vidMatch = pageContent.match(/var\s+vid\s*=\s*["\']?(\d+)["\']?/);
        const aidMatch = pageContent.match(/var\s+playlistId\s*=\s*["\']?(\d+)["\']?/);

        if (!vidMatch || !aidMatch) {
          log("error", "[Sohu] 无法从页面中提取vid或aid");
          return [];
        }

        vid = vidMatch[1];
        aid = aidMatch[1];
      } else {
        // 2. 处理数字 episodeId
        const episodeId = parseInt(url);
        let foundLink = null;

        for (const anime of globals.animes) {
          if (anime.links) {
            foundLink = anime.links.find(link => link.id === episodeId);
            if (foundLink) {
              log("info", `[Sohu] 找到 episodeId ${episodeId} 对应的URL: ${foundLink.url}`);
              return await this.getEpisodeDanmu(foundLink.url);
            }
          }
        }

        if (!foundLink) {
          log("error", `[Sohu] 未找到 episodeId ${episodeId} 对应的URL`);
          return [];
        }
      }

      log("info", `[Sohu] 解析得到 vid=${vid}, aid=${aid}`);

      // 并发请求弹幕
      const maxTime = 7200; // 最大2小时
      const segmentDuration = 60;
      const allComments = [];
      let consecutiveEmptySegments = 0; 
      const concurrency = 6; 

      log("info", `[Sohu] 开始并发获取弹幕 (并发数: ${concurrency})`);

      for (let batchStart = 0; batchStart < maxTime; batchStart += (segmentDuration * concurrency)) {
        const promises = [];

        for (let i = 0; i < concurrency; i++) {
          const currentStart = batchStart + (i * segmentDuration);
          if (currentStart >= maxTime) break;
          const currentEnd = currentStart + segmentDuration;

          const p = this._getDanmuSegment(vid, aid, currentStart, currentEnd)
            .then(items => ({ start: currentStart, items: items || [] }))
            .catch(err => {
              log("warn", `[Sohu] 获取片段 ${currentStart}s 失败: ${err.message}`);
              return { start: currentStart, items: [] };
            });

          promises.push(p);
        }

        const batchResults = await Promise.all(promises);
        batchResults.sort((a, b) => a.start - b.start);

        let stopFetching = false;

        for (const result of batchResults) {
          if (result.items.length > 0) {
            allComments.push(...result.items);
            consecutiveEmptySegments = 0;
          } else {
            consecutiveEmptySegments++;
            if (consecutiveEmptySegments >= 3 && result.start >= 600) {
              stopFetching = true;
            }
          }
        }

        if (allComments.length > 0 && batchStart % 600 === 0) {
          log("info", `[Sohu] 已扫描至 ${Math.min(batchStart + (segmentDuration * concurrency), maxTime) / 60} 分钟, 累计弹幕: ${allComments.length}`);
        }

        if (stopFetching) {
          log("info", `[Sohu] 连续无弹幕，提前结束获取 (位置: ${(batchStart / 60).toFixed(1)} 分钟)`);
          break;
        }
      }

      if (allComments.length === 0) {
        log("info", "[Sohu] 该视频暂无弹幕数据");
        return [];
      }

      log("info", `[Sohu] 共获取 ${allComments.length} 条原始弹幕`);

      // 调试：打印前3条原始弹幕
      if (allComments.length > 0) {
        log("debug", `[Sohu] 前3条原始弹幕示例: ${JSON.stringify(allComments.slice(0, 3))}`);
      }

      return allComments;

    } catch (error) {
      log("error", "[Sohu] 获取弹幕出错:", error.message);
      return [];
    }
  }

  /**
   * 获取单个时间段的弹幕
   */
  async _getDanmuSegment(vid, aid, start, end) {
    try {
      const params = new URLSearchParams({
        act: 'dmlist_v2',
        vid: vid,
        aid: aid,
        pct: '2',
        time_begin: String(start),
        time_end: String(end),
        dct: '1',
        request_from: 'h5_js'
      });

      const url = `${this.danmuApiUrl}?${params.toString()}`;

      const response = await httpGet(url, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'Referer': 'https://tv.sohu.com/'
        },
        timeout: 10000
      });

      if (!response || !response.data) {
        return [];
      }

      const data = typeof response.data === "string" ? JSON.parse(response.data) : response.data;
      // 兼容 info 包装层级
      const comments = data?.info?.comments || data?.comments || [];
      
      if (comments.length > 0) {
        log("debug", `[Sohu] 获取时间段 ${start}-${end}s 的弹幕: ${comments.length} 条`);
      }
      
      return comments;

    } catch (error) {
      log("warn", `[Sohu] 获取时间段 ${start}-${end}s 弹幕失败: ${error.message}`);
      return [];
    }
  }

  /**
   * 解析弹幕颜色
   */
  parseColor(item) {
    try {
      const colorStr = item.color || item.cl || item.c || '';
      if (!colorStr) return 16777215;

      if (typeof colorStr === 'string') {
        const hex = colorStr.replace('#', '');
        const decimal = parseInt(hex, 16);
        return isNaN(decimal) ? 16777215 : decimal;
      }

      if (typeof colorStr === 'number') {
        return colorStr;
      }

      return 16777215;
    } catch (error) {
      return 16777215;
    }
  }

  /**
   * 格式化弹幕 - 返回标准中间格式
   */
  formatComments(comments) {
    if (!comments || !Array.isArray(comments)) {
      log("warn", "[Sohu] formatComments 接收到无效的 comments 参数");
      return [];
    }

    const formatted = [];

    for (const item of comments) {
      try {
        // 1. 提取内容 - 尝试多个可能的字段
        let text = item.content || item.ct || item.c || item.m || item.text || item.msg || item.message || '';

        // 如果 text 是对象，尝试提取其中的文本
        if (typeof text === 'object' && text !== null) {
          text = text.content || text.text || text.msg || JSON.stringify(text);
        }

        // 确保转为字符串
        if (typeof text !== 'string') {
          text = String(text);
        }

        // 移除空弹幕
        text = text.trim();
        if (!text || text === '' || text === 'null' || text === 'undefined') {
          continue;
        }

        // 2. 提取时间（秒）
        let timepoint = parseFloat(item.v || item.time || item.timepoint || 0);

        // 3. 提取颜色
        const color = this.parseColor(item);

        // 4. 提取发送时间戳
        let unixtime = parseInt(item.created || item.timestamp || item.unixtime || 0);
        if (unixtime === 0) {
          unixtime = Math.floor(Date.now() / 1000);
        }

        // 5. 提取用户ID
        const uid = String(item.uid || item.user_id || '0');

        // 6. 提取弹幕类型（1=滚动，4=底部，5=顶部）
        let ct = 1; // 默认滚动
        if (item.mode !== undefined) {
          ct = parseInt(item.mode);
        } else if (item.ct !== undefined) {
          ct = parseInt(item.ct);
        }

        // 7. 构建标准中间格式对象
        const danmuObj = {
          timepoint: timepoint,
          ct: ct,
          size: 25,
          color: color,
          unixtime: unixtime,
          uid: uid,
          content: text
        };

        formatted.push(danmuObj);

      } catch (error) {
        log("debug", `[Sohu] 格式化单条弹幕出错: ${error.message}`);
      }
    }

    log("info", `[Sohu] 成功格式化 ${formatted.length} 条弹幕`);

    // 调试：打印前3条格式化后的弹幕
    if (formatted.length > 0) {
      log("debug", `[Sohu] 前3条格式化后的弹幕: ${JSON.stringify(formatted.slice(0, 3))}`);
    }

    return formatted;
  }
}