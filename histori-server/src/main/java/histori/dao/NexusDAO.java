package histori.dao;

import histori.dao.archive.NexusArchiveDAO;
import histori.dao.shard.NexusShardDAO;
import histori.model.Account;
import histori.model.Nexus;
import histori.model.NexusTag;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.ShardSetConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.validation.Valid;
import java.util.Iterator;
import java.util.List;

import static histori.model.CanonicalEntity.canonicalize;
import static histori.model.TagType.EVENT_TYPE;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;

@Repository @Slf4j
public class NexusDAO extends ShardedEntityDAO<Nexus, NexusShardDAO> {

    @Autowired private NexusArchiveDAO nexusArchiveDAO;

    @Autowired private DatabaseConfiguration database;
    @Override protected ShardSetConfiguration getShardConfiguration() { return database.getShard("nexus"); }

    @Autowired @Getter @Setter private SuperNexusDAO superNexusDAO;
    @Autowired @Getter @Setter private TagDAO tagDAO;
    @Autowired @Getter @Setter private RedisService redisService;

    @Getter(lazy=true) private final RedisService nexusCache = initNexusCache();
    private RedisService initNexusCache() { return redisService.prefixNamespace("nexus-cache:", null); }

    @Override public Object preCreate(@Valid Nexus entity) {
        entity.prepareForSave();

        // ensure tag is present, or create it if not
        if (entity.hasNexusType()) {
            return new NexusTag().setTagName(entity.getNexusType()).setTagType(EVENT_TYPE);
        }

        // create version
        VersionedEntityDAO.incrementVersionAndArchive(entity, this, nexusArchiveDAO);

        return super.preCreate(entity);
    }

    @Override public Object preUpdate(@Valid Nexus entity) {
        entity.prepareForSave();

        // ensure event_type tag corresponding to nexusType is present, or create it if not
        if (entity.hasNexusType()) {
            // what tags already exist?
            final NexusTag typeTag = new NexusTag().setTagName(entity.getNexusType()).setTagType(EVENT_TYPE);
            if (!entity.hasExactTag(typeTag)) {
                entity.addTag(typeTag);
                return typeTag;
            } else {
                // nexusType already matches one of the event_type tags
            }
        } else {
            entity.setNexusType(entity.getFirstEventType());
        }

        // create version
        VersionedEntityDAO.incrementVersionAndArchive(entity, this, nexusArchiveDAO);

        return super.preUpdate(entity);
    }

    @Override public Nexus postCreate(Nexus nexus, Object context) {
        postProcessNexus(nexus);
        return super.postCreate(nexus, context);
    }

    @Override public Nexus postUpdate(Nexus nexus, Object context) {
        postProcessNexus(nexus);
        return super.postUpdate(nexus, context);
    }

    public void postProcessNexus(Nexus nexus) {
        getNexusCache().set(nexus.getUuid(), toJsonOrDie(nexus));
        superNexusDAO.updateSuperNexus(nexus);
        tagDAO.updateTags(nexus);
    }

    public Nexus findByOwnerAndName(Account account, String name) {
        return findByUniqueFields("owner", account.getUuid(), "canonicalName", canonicalize(name));
    }

    public List<Nexus> findByName(String name) {
        return findByField("canonicalName", canonicalize(name));
    }

    public List<Nexus> findByCanonicalName(String canonicalName) { return findByField("canonicalName", canonicalName); }

    public List<Nexus> findByNameAndVisibleToAccount(String name, Account account) {
        final List<Nexus> found = findByField("canonicalName", canonicalize(name));
        for (Iterator<Nexus> iter = found.iterator(); iter.hasNext(); ) {
            if (!iter.next().isVisibleTo(account)) iter.remove();
        }
        return found;
    }

}
