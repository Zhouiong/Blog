package cn.lzzy.service.impl;

import cn.lzzy.dao.CommentMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import cn.lzzy.dao.ArticleMapper;
import cn.lzzy.dao.StatisticMapper;
import cn.lzzy.model.domain.Article;
import cn.lzzy.model.domain.Statistic;
import cn.lzzy.service.IArticleService;
import com.vdurmont.emoji.EmojiParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author 周世雄
 * @date 2023/10/27 12:00
 * 博客： https://blog.csdn.net/m0_60482574?type=blog
 * @description: 网站开发与维护
 */
@Service
@Transactional
public class ArticleServiceImpl implements IArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private StatisticMapper statisticMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CommentMapper commentMapper;

    // 分页查询文章列表
    @Override
    public PageInfo<Article> selectArticleWithPage(Integer page, Integer count) {
        PageHelper.startPage(page, count);
        List<Article> articleList = articleMapper.selectArticleWithPage();
        // 封装文章统计数据
        for (int i = 0; i < articleList.size(); i++) {
            Article article = articleList.get(i);
            Statistic statistic = statisticMapper.selectStatisticWithArticleId(article.getId());
            article.setHits(statistic.getHits());
            article.setCommentsNum(statistic.getCommentsNum());
        }
        PageInfo<Article> pageInfo = new PageInfo<>(articleList);
        return pageInfo;
    }

    // 统计前15的热度文章信息
    @Override
    public List<Article> getHeatArticles() {
        List<Statistic> list = statisticMapper.getStatistic();
        List<Article> articlelist = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Article article = articleMapper.selectArticleWithId(list.get(i).getArticleId());
            article.setHits(list.get(i).getHits());
            article.setCommentsNum(list.get(i).getCommentsNum());
            articlelist.add(article);
            if (i >= 14) {
                break;
            }
        }
        return articlelist;
    }

    // 根据id查询单个文章详情，并使用Redis进行缓存管理
    public Article selectArticleWithId(Integer id){
        Article article = null;
        Object o = redisTemplate.opsForValue().get("article_" + id);
        if(o!=null){
            article=(Article)o;
        }else{
            article = articleMapper.selectArticleWithId(id);
            if(article!=null){
                redisTemplate.opsForValue().set("article_" + id,article);
            }
        }
        return article;
    }
    // 发布文章
    @Override
    public void publish(Article article) {
        // 去除表情
        article.setContent(EmojiParser.parseToAliases(article.getContent()));
        article.setCreated(new Date());
        article.setHits(0);
        article.setCommentsNum(0);
        // 插入文章，同时插入文章统计数据
        articleMapper.publishArticle(article);
        statisticMapper.addStatistic(article);
    }

    // 更新文章
    @Override
    public void updateArticleWithId(Article article) {
        article.setModified(new Date());
        articleMapper.updateArticleWithId(article);
        redisTemplate.delete("article_" + article.getId());
    }

    // 删除文章
    @Override
    public void deleteArticleWithId(int id) {
        // 删除文章的同时，删除对应的缓存
        articleMapper.deleteArticleWithId(id);
        redisTemplate.delete("article_" + id);
        // 同时删除对应文章的统计数据
        statisticMapper.deleteStatisticWithId(id);
        // 同时删除对应文章的评论数据
        commentMapper.deleteCommentWithId(id);
    }

}
