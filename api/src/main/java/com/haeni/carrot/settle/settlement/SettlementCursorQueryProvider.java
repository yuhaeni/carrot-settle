package com.haeni.carrot.settle.settlement;

import com.haeni.carrot.settle.domain.settlement.Settlement;
import jakarta.persistence.Query;
import org.springframework.batch.infrastructure.item.database.orm.AbstractJpaQueryProvider;

class SettlementCursorQueryProvider extends AbstractJpaQueryProvider {

  private long lastId = 0L;

  void setLastId(long lastId) {
    this.lastId = lastId;
  }

  @Override
  public Query createQuery() {
    return getEntityManager()
        .createNamedQuery(Settlement.QUERY_FIND_INCOMPLETED_BEFORE_BY_CURSOR, Settlement.class)
        .setParameter("lastId", lastId);
  }

  @Override
  public void afterPropertiesSet() {}
}
