package histori.model.archive;

import histori.model.VersionedEntity;
import histori.model.base.VoteBase;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.shard.Shardable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity @Accessors(chain=true)
@Table(uniqueConstraints = @UniqueConstraint(name = "vote_archive_uniq", columnNames = {"owner", "entity", "version"}))
public class VoteArchive extends VoteBase implements EntityArchive, Shardable {

    @Override public String getHashToShardField() { return "identifier"; }

    @Column(length=UUID_MAXLEN*10, nullable=false, updatable=false)
    @Getter @Setter private String identifier;

    @Override public String getIdentifier(VersionedEntity entity) { return entity.getUuid(); }
}
