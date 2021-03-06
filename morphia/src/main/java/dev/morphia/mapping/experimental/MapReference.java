package dev.morphia.mapping.experimental;

import com.mongodb.DBRef;
import com.mongodb.client.MongoCursor;
import dev.morphia.Datastore;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.MappedField;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.codec.references.ReferenceCodec;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static dev.morphia.query.experimental.filters.Filters.in;

/**
 * @param <T>
 * @morphia.internal
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MapReference<T> extends MorphiaReference<Map<Object, T>> {
    private Map<String, Object> ids;
    private Map<Object, T> values;
    private Map<String, List<Object>> collections = new HashMap<>();

    /**
     * @param datastore   the datastore to use
     * @param ids         the IDs of the entities
     * @param mappedClass the MappedClass for the entity type
     * @morphia.internal
     */
    public MapReference(final Datastore datastore, final Map<String, Object> ids, final MappedClass mappedClass) {
        super(datastore);
        if (ids != null) {
            //            if (ids.entrySet().stream().allMatch(mappedClass.getType()::isInstance)) {
            //                setValues(ids);
            //            } else {
            for (final Entry<String, Object> entry : ids.entrySet()) {
                CollectionReference.collate(mappedClass, collections, entry.getValue());
            }
            this.ids = ids;
            //            }
        }

    }

    private void setValues(final Map<String, Object> values) {
        resolve();
    }

    MapReference(final Map<Object, T> values) {
        this.values = values;
    }

    /**
     * Decodes a document in to entities
     *
     * @param datastore   the datastore
     * @param mapper      the mapper
     * @param mappedField the MappedField
     * @param document    the Document to decode
     * @return the entities
     */
    public static MapReference decode(final Datastore datastore, final Mapper mapper, final MappedField mappedField,
                                      final Document document) {
        final Class subType = mappedField.getTypeData().getTypeParameters().get(0).getType();

        final Map<String, Object> ids = (Map<String, Object>) mappedField.getDocumentValue(document);
        MapReference reference = null;
        if (ids != null) {
            reference = new MapReference(datastore, ids, mapper.getMappedClass(subType));
        }

        return reference;
    }

    /**
     * {@inheritDoc}
     */
    public Map<Object, T> get() {
        if (values == null && ids != null) {
            values = new LinkedHashMap<>();
            mergeReads();
        }
        return values;
    }

    @Override
    public Class<Map<Object, T>> getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> getIds() {
        return new ArrayList<>(ids.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object encode(final Mapper mapper, final Object value, final MappedField field) {
        if (isResolved()) {
            Map<String, Object> ids = new LinkedHashMap<>();
            for (final Entry<Object, T> entry : get().entrySet()) {
                ids.put(entry.getKey().toString(), wrapId(mapper, field, entry.getValue()));
            }
            return ids;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> getId(final Mapper mapper, final Datastore datastore, final MappedClass field) {
        if (ids == null) {
            ids = new LinkedHashMap<>();
            values.entrySet().stream()
                  .forEach(e -> ids.put(e.getKey().toString(),
                      ReferenceCodec.encodeId(mapper, datastore, e.getValue(), field)));
        }
        return ids;
    }

    private void mergeReads() {
        for (final Entry<String, List<Object>> entry : collections.entrySet()) {
            readFromSingleCollection(entry.getKey(), entry.getValue());
        }
        resolve();
    }

    @SuppressWarnings("unchecked")
    private void readFromSingleCollection(final String collection, final List<Object> collectionIds) {

        try (MongoCursor<T> cursor = (MongoCursor<T>) getDatastore().find(collection)
                                                                    .filter(in("_id", collectionIds)).iterator()) {
            final Map<Object, T> idMap = new HashMap<>();
            while (cursor.hasNext()) {
                final T entity = cursor.next();
                idMap.put(getDatastore().getMapper().getId(entity), entity);
            }

            for (final Entry<String, Object> entry : ids.entrySet()) {
                final Object id = entry.getValue();
                final T value = idMap.get(id instanceof DBRef ? ((DBRef) id).getId() : id);
                if (value != null) {
                    values.put(entry.getKey(), value);
                }
            }
        }
    }

}
