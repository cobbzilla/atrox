package histori.model.tag_schema;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum TagSchemaFieldType {

    integer, decimal, world_actor, world_event, event_type, idea, impact, result, role_type, relationship_type, citation, tag;

    @JsonCreator public static TagSchemaFieldType create (String val) { return valueOf(val.toLowerCase()); }

}