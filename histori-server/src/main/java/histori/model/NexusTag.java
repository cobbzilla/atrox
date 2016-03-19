package histori.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import histori.model.tag_schema.TagSchemaValue;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.mappy.MappySortedSet;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.*;

import static histori.ApiConstants.NAME_MAXLEN;
import static histori.model.CanonicalEntity.canonicalize;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@Accessors(chain=true)
public class NexusTag extends IdentifiableBase implements Comparable<NexusTag> {

    @Override public int compareTo(NexusTag o) { return getCanonicalName().compareTo(o.getCanonicalName()); }

    @Size(max=NAME_MAXLEN, message="err.tagName.tooLong")
    @Column(length=NAME_MAXLEN, nullable=false, updatable=false)
    @Getter private String tagName;

    public NexusTag setTagName(String tagName) {
        this.tagName = tagName;
        this.canonicalName = canonicalize(tagName);
        return this;
    }

    @Size(max=NAME_MAXLEN, message="err.tagName.tooLong")
    @Column(length=NAME_MAXLEN, nullable=false, updatable=false)
    @Getter @Setter private String canonicalName;

    @Transient
    public String getDisplayName () { return tagName.replace("_", " ").trim(); }
    public void setDisplayName (String name) {/* noop */}

    // denormalized
    @Size(max=NAME_MAXLEN, message="err.tagType.tooLong")
    @Column(length=NAME_MAXLEN)
    @Getter @Setter private String tagType;
    public boolean hasTagType () { return !empty(tagType); }

    @HasValue(message="err.schemaValue.empty")
    @Size(min=2, max=32000, message="err.schemaValues.tooLong")
    @Column(length=32000)
    @JsonIgnore
    @Getter @Setter private String schemaValues = "[]";
    public boolean hasSchemaValues () { return !empty(getValues()); }

    @Transient
    public TagSchemaValue[] getValues () { return empty(schemaValues) ? null : fromJsonOrDie(schemaValues, TagSchemaValue[].class); }
    public NexusTag setValues (TagSchemaValue[] values) { return setSchemaValues(empty(values) ? null : toJsonOrDie(values)); }

    public NexusTag setValue(String field, String value) {
        TagSchemaValue[] values = getValues();
        if (values == null) {
            values = new TagSchemaValue[] { new TagSchemaValue(field, value) };
        } else {
            values = ArrayUtil.append(values, new TagSchemaValue(field, value));
        }
        setValues(values);
        return this;
    }

    @Override public String toString() {
        if (hasSchemaValues()) {
            return getTagType() + "/" + getTagName() + "(schema:"+getSchemaValueMap()+")";
        } else {
            return getTagType() + "/" + getTagName();
        }
    }

    @JsonIgnore @Transient public SchemaValueMap getSchemaValueMap() {
        final SchemaValueMap map = new SchemaValueMap();
        if (!hasSchemaValues()) return map;
        map.addAll(getValues());
        return map;
    }

    @JsonIgnore @Transient public String getSchemaHash() {
        return getSchemaValueMap().getHash();
    }

    public static List<NexusTag> filterByType(List<NexusTag> tags, String type) {
        final List<NexusTag> found = new ArrayList<>();
        if (!empty(tags)) {
            for (NexusTag tag : tags) {
                if (tag.getTagType().equalsIgnoreCase(type)) found.add(tag);
            }
        }
        return found;
    }

    public static boolean containsTypeTag(List<NexusTag> tags, String type) {
        if (!empty(tags)) {
            for (NexusTag tag : tags) if (tag.getTagType().equalsIgnoreCase(type)) return true;
        }
        return false;
    }

    public boolean isSameTag(NexusTag tag) {
        if (!canonicalize(getTagName()).equals(canonicalize(tag.getTagName()))) return false;
        if (!canonicalize(getTagType()).equals(canonicalize(tag.getTagType()))) return false;
        // blank schema values matches any other tag
        return !hasSchemaValues() || !tag.hasSchemaValues() || getSchemaHash().equals(tag.getSchemaHash());
    }

    public class SchemaValueMap extends MappySortedSet<String, String> {

        public void addAll(TagSchemaValue[] values) {
            for (TagSchemaValue val : values) {
                put(val.getField(), val.getValue());
            }
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof SchemaValueMap)) return false;

            final SchemaValueMap other = (SchemaValueMap) o;

            if (other.size() != this.size()) return false;

            for (String field : this.keySet()) {
                final Set<String> values = this.getAll(field);
                final Set<String> otherVals = other.getAll(field);

                if (values.size() != otherVals.size()) return false;
                for (String val : values) if (!otherVals.contains(val)) return false;
                for (String val : otherVals) if (!values.contains(val)) return false;
            }

            for (String field : other.keySet()) {
                final Set<String> values = this.getAll(field);
                final Set<String> otherVals = other.getAll(field);

                if (values.size() != otherVals.size()) return false;
                for (String val : values) if (!otherVals.contains(val)) return false;
                for (String val : otherVals) if (!values.contains(val)) return false;
            }

            return true;
        }

        private static final String KSEP = "|||";
        private static final String VSEP = "@@@";

        @Override public int hashCode() { return getHash().hashCode(); }

        public String getHash() {
            final StringBuilder hash = new StringBuilder();
            for (String key : keySet()) {
                hash.append(KSEP).append(key);
                for (String value : getAll(key)) {
                    hash.append(VSEP).append(value);
                }
            }
            return sha256_hex(hash.toString());
        }
    }

}
