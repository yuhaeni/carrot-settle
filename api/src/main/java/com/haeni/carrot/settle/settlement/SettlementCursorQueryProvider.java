package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import jakarta.persistence.Query;
import org.springframework.batch.infrastructure.item.database.orm.AbstractJpaQueryProvider;

class SettlementCursorQueryProvider extends AbstractJpaQueryProvider {

  private static final String JPQL =
      "SELECT s FROM Settlement s "
          + "WHERE s.status = :status "
          + "AND s.skipCount < :skipThreshold "
          + "AND s.settlementDate < :targetDate "
          + "AND s.id > :lastId "
          + "ORDER BY s.id";

  private long lastId = 0L;

  void setLastId(long lastId) {
    this.lastId = lastId;
  }

  @Override
  public Query createQuery() {
    return getEntityManager().createQuery(JPQL, Settlement.class).setParameter("lastId", lastId);
  }

  @Override
  public void afterPropertiesSet() {}
}
