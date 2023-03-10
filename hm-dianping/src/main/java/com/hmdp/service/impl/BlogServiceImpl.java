package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);

    }

    private void isBlogLiked(Blog blog) {

        Long userId;
        try {
            // 用户可能没登陆 这时候取不到用户
            userId = UserHolder.getUser().getId();
        } catch (NullPointerException e) {
            blog.setLiked(BooleanUtil.toInt(false));
            return;
        }


        String key = BLOG_LIKED_KEY + blog.getId();
        // 判断用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 判断用户是否已经点赞
        Long userId = UserHolder.getUser().getId();

        String key = BLOG_LIKED_KEY + id;

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 如果未点赞 则可以点赞
            // 修改点赞数 + 1
            boolean isSucceed = update().setSql("liked = liked + 1").eq("id", id).update();

            // 保存用户到redis
            if (isSucceed) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }

        } else {
            // 如果已点赞
            // 修改点赞数 -1
            boolean isSucceed = update().setSql("liked = liked - 1").eq("id", id).update();

            // 从redis里移除
            if (isSucceed) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 获取点赞前五
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析其中userid
        List<Long> ids = top5.stream().map(item -> Long.valueOf(item)).collect(Collectors.toList());

        // 根据userId查询数据库  重点：还需要按顺序，目前ids是有序的但是数据库in查询无序
        // select * from user where id in (5,8,3,1) order by FIELD (5,8,3,1) 才会有序
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD (id," + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {

        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSucceed = save(blog);

        if (!isSucceed) {
            return Result.fail("新增笔记失败");
        }
        // 查询笔记作者的所有粉丝
        // select * from tb_follow where follow_user_id = ?
        List<Follow> followUsers = followService.query().eq("follow_user_id", user.getId().toString()).list();
        for (Follow followUser : followUsers) {
            // 获取每一个关注"我"的用户
            Long userId = followUser.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBolgOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long id = UserHolder.getUser().getId();
        // 找到收件箱
        String key = FEED_KEY + id;

        // 滚动分页查询 ZREVRANGE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 解析数据 blogId，时间戳，offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            // 获取分数
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            }else {
                minTime = time;
                os = 1;
            }

        }

        // 根据blogId获取bolg
        String idsStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD (id," + idsStr + ")").list();

        // 注入blog点赞数据和用户数据
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        // 封装数据
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        scrollResult.setList(blogs);
        // 返回
        return Result.ok(scrollResult);
    }
}
