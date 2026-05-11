package com.haeni.carrot.settle.infrastructure.settlement;

import com.haeni.carrot.settle.domain.settlement.QSettlement;
import com.haeni.carrot.settle.domain.settlement.Settlement;
import com.haeni.carrot.settle.domain.settlement.SettlementStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class SettlementRepositoryImpl implements SettlementRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  public SettlementRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public Page<Settlement> search(
      Long sellerId,
      LocalDate startDate,
      LocalDate endDate,
      SettlementStatus status,
      Pageable pageable) {

    QSettlement s = QSettlement.settlement;
    BooleanBuilder builder = new BooleanBuilder();
    if (sellerId != null) {
      builder.and(s.seller.id.eq(sellerId));
    }
    if (startDate != null) {
      builder.and(s.settlementDate.goe(startDate));
    }
    if (endDate != null) {
      builder.and(s.settlementDate.loe(endDate));
    }
    if (status != null) {
      builder.and(s.status.eq(status));
    }

    List<Settlement> content =
        queryFactory
            .selectFrom(s)
            .leftJoin(s.seller)
            .fetchJoin()
            .where(builder)
            .orderBy(s.settlementDate.desc(), s.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    Long total = queryFactory.select(s.count()).from(s).where(builder).fetchOne();

    return new PageImpl<>(content, pageable, total != null ? total : 0L);
  }
}
