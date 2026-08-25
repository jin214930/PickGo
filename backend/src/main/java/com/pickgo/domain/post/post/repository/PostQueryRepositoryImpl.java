package com.pickgo.domain.post.post.repository;

import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.performance.entity.QPerformance;
import com.pickgo.domain.performance.venue.entity.QVenue;
import com.pickgo.domain.post.post.entity.Post;
import com.pickgo.domain.post.post.entity.PostSortType;
import com.pickgo.domain.post.post.entity.QPost;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class PostQueryRepositoryImpl implements PostQueryRepository {
    private static final int OPENING_SOON_LIMIT = 5;

    private final JPAQueryFactory queryFactory;

    private final QPost post = QPost.post;
    private final QPerformance performance = QPerformance.performance;
    private final QVenue venue = QVenue.venue;


    @Override
    public Page<Post> searchPosts(Pageable pageable, String keyword, PerformanceType type, PostSortType sort) {
        BooleanBuilder condition = new BooleanBuilder()
                .and(post.isPublished.isTrue());

        if (StringUtils.hasText(keyword)) {
            condition.and(
                    Expressions
                            .stringTemplate(
                                    "LOWER(REPLACE({0}, ' ', ''))",
                                    post.title
                            )
                            .contains(keyword)
            );
        }

        if (type != null) {
            condition.and(performance.type.eq(type));
        }

        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.performance, performance)
                .fetchJoin()
                .join(performance.venue, venue)
                .fetchJoin()
                .where(condition)
                .orderBy(toOrderSpecifiers(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(post.count())
                .from(post);

        if (type != null) {
            countQuery.join(post.performance, performance);
        }

        Long total = countQuery
                .where(condition)
                .fetchOne();

        return new PageImpl<>(
                content,
                pageable,
                total == null ? 0L : total
        );
    }

    @Override
    public List<Post> findPopularPosts(int size, PerformanceType type) {
        BooleanBuilder condition = new BooleanBuilder()
                .and(post.isPublished.isTrue());

        if (type != null) {
            condition.and(performance.type.eq(type));
        }

        return queryFactory
                .selectFrom(post)
                .join(post.performance, performance)
                .fetchJoin()
                .join(performance.venue, venue)
                .fetchJoin()
                .where(condition)
                .orderBy(post.views.desc(), post.id.desc())
                .limit(size)
                .fetch();
    }

    @Override
    public List<Post> findOpeningSoonPosts() {
        return queryFactory
                .selectFrom(post)
                .join(post.performance, performance)
                .fetchJoin()
                .join(performance.venue, venue)
                .fetchJoin()
                .where(
                        post.isPublished.isTrue(),
                        performance.state.eq(PerformanceState.SCHEDULED)
                )
                .orderBy(performance.startDate.asc(), post.id.desc())
                .limit(OPENING_SOON_LIMIT)
                .fetch();
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(PostSortType sort) {
        if (sort == null) {
            return new OrderSpecifier<?>[]{post.id.desc()};
        }

        return switch (sort) {
            case ID_DESC -> new OrderSpecifier<?>[]{post.id.desc()};
            case VIEW_DESC -> new OrderSpecifier<?>[]{post.views.desc(), post.id.desc()};
            case OPENING_SOON -> new OrderSpecifier<?>[]{performance.startDate.asc(), post.id.desc()};
        };
    }
}
