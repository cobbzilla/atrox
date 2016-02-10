package atrox.dao.history;

import atrox.dao.SocialEntityDAO;
import atrox.model.Account;
import atrox.model.canonical.CanonicalEntity;
import atrox.model.history.EntityHistory;
import atrox.model.support.EntitySearchOrder;
import atrox.model.support.EntitySearchType;
import atrox.model.support.EntityVisibility;
import atrox.server.AtroxConfiguration;
import edu.emory.mathcs.backport.java.util.Collections;
import org.cobbzilla.util.collection.SingletonList;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public abstract class EntityHistoryDAO<H extends EntityHistory> extends SocialEntityDAO<H> {

    @Autowired protected AtroxConfiguration configuration;

    public H findByCanonical(Account account, CanonicalEntity entity) {
        return uniqueResult(criteria().add(Restrictions.and(
                Restrictions.eq(getEntityProto().getCanonicalField(), entity.getUuid()),
                Restrictions.eq("owner", account.getUuid())
        )));
    }

    public List<H> findAllByCanonical(Account account, CanonicalEntity canonical, EntitySearchType searchType, EntitySearchOrder searchOrder) {

        switch (searchType) {
            case mine: default:
                return new SingletonList<>(findByCanonical(account, canonical));
            case none:
                return Collections.emptyList();
            case all:
                return list(criteria().add(Restrictions.and(
                        Restrictions.eq(getEntityProto().getCanonicalField(), canonical.getUuid()),
                        Restrictions.or(
                                Restrictions.eq("owner", account.getUuid()),
                                Restrictions.eq("visibility", EntityVisibility.everyone))))
                        .addOrder(getOrder(searchOrder)));

            case others:
                return list(criteria().add(Restrictions.and(
                        Restrictions.eq(getEntityProto().getCanonicalField(), canonical.getUuid()),
                        Restrictions.ne("owner", account.getUuid()),
                        Restrictions.eq("visibility", EntityVisibility.everyone)))
                        .addOrder(getOrder(searchOrder)));
        }

    }

    public Order getOrder(EntitySearchOrder searchOrder) {
        switch (searchOrder) {
            case newest: default: return Order.desc("ctime");
            case oldest: return Order.asc("ctime");
            case most_upvotes: return Order.desc("votes.up_votes");
            case most_downvotes: return Order.desc("votes.down_votes");
        }
    }
}
