package com.linkedin.metadata.kafka.hook;

import com.datahub.util.RecordUtils;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.schema.PathSpec;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.graph.Edge;
import com.linkedin.metadata.models.RelationshipFieldSpec;
import com.linkedin.mxe.MetadataChangeLog;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class GraphIndexUtils {

  private GraphIndexUtils() { }

  @Nullable
  private static List<Urn> getActorList(@Nullable final String path, @Nonnull final RecordTemplate aspect) {
    if (path == null) {
      return null;
    }
    final PathSpec actorPathSpec = new PathSpec(path.split("/"));
    final Object value = RecordUtils.getNullableFieldValue(aspect, actorPathSpec);
    return (List<Urn>) value;
  }

  @Nullable
  private static List<Long> getTimestampList(@Nullable final String path, @Nonnull final RecordTemplate aspect) {
    if (path == null) {
      return null;
    }
    final PathSpec timestampPathSpec = new PathSpec(path.split("/"));
    final Object value = RecordUtils.getNullableFieldValue(aspect, timestampPathSpec);
    return (List<Long>) value;
  }

  @Nullable
  private static List<Map<String, Object>> getPropertiesList(@Nullable final String path, @Nonnull final RecordTemplate aspect) {
    if (path == null) {
      return null;
    }
    final PathSpec propertiesPathSpec = new PathSpec(path.split("/"));
    final Object value = RecordUtils.getNullableFieldValue(aspect, propertiesPathSpec);
    return (List<Map<String, Object>>) value;
  }



  @Nullable
  private static boolean isValueListValid(@Nullable final List<?> entryList, final int valueListSize) {
    if (entryList == null) {
      return false;
    }
    if (valueListSize != entryList.size()) {
      return false;
    }
    return true;
  }

  @Nullable
  private static Long getTimestamp(@Nullable final List<Long> timestampList, final int index, final int valueListSize) {
    if (isValueListValid(timestampList, valueListSize)) {
      return timestampList.get(index);
    }
    return null;
  }

  @Nullable
  private static Urn getActor(@Nullable final List<Urn> actorList, final int index, final int valueListSize) {
    if (isValueListValid(actorList, valueListSize)) {
      return actorList.get(index);
    }
    return null;
  }

  @Nullable
  private static Map<String, Object> getProperties(@Nullable final List<Map<String, Object>> propertiesList, final int index, final int valueListSize) {
    if (isValueListValid(propertiesList, valueListSize)) {
      return propertiesList.get(index);
    }
    return null;
  }

  /**
   * Used to create new edges for the graph db, adding all the metadata associated with each edge based on the aspect.
   * Returns a list of Edges to be consumed by the graph service.
   */
  @Nonnull
  public static List<Edge> extractGraphEdges(
      @Nonnull final Map.Entry<RelationshipFieldSpec, List<Object>> extractedFieldsEntry,
      @Nonnull final RecordTemplate aspect,
      @Nonnull final Urn urn,
      @Nonnull final MetadataChangeLog event
  ) {
    final List<Edge> edgesToAdd = new ArrayList<>();
    final String createdOnPath = extractedFieldsEntry.getKey().getRelationshipAnnotation().getCreatedOn();
    final String createdActorPath = extractedFieldsEntry.getKey().getRelationshipAnnotation().getCreatedActor();
    final String updatedOnPath = extractedFieldsEntry.getKey().getRelationshipAnnotation().getUpdatedOn();
    final String updatedActorPath = extractedFieldsEntry.getKey().getRelationshipAnnotation().getUpdatedActor();
    final String propertiesPath = extractedFieldsEntry.getKey().getRelationshipAnnotation().getProperties();

    final List<Long> createdOnList = getTimestampList(createdOnPath, aspect);
    final List<Urn> createdActorList = getActorList(createdActorPath, aspect);
    final List<Long> updatedOnList = getTimestampList(updatedOnPath, aspect);
    final List<Urn> updatedActorList = getActorList(updatedActorPath, aspect);
    final List<Map<String, Object>> propertiesList = getPropertiesList(propertiesPath, aspect);

    int index = 0;
    for (Object fieldValue : extractedFieldsEntry.getValue()) {
      Long createdOn = createdOnList != null ? getTimestamp(createdOnList, index, extractedFieldsEntry.getValue().size()) : null;
      Urn createdActor = createdActorList != null ? getActor(createdActorList, index, extractedFieldsEntry.getValue().size()) : null;
      final Long updatedOn = updatedOnList != null ? getTimestamp(updatedOnList, index, extractedFieldsEntry.getValue().size()) : null;
      final Urn updatedActor = updatedActorList != null ? getActor(updatedActorList, index, extractedFieldsEntry.getValue().size()) : null;
      final Map<String, Object> properties = propertiesList != null ? getProperties(propertiesList, index, extractedFieldsEntry.getValue().size()) : null;

      if (createdOn == null && event.hasSystemMetadata()) {
        createdOn = event.getSystemMetadata().getLastObserved();
      }
      if (createdActor == null && event.hasCreated()) {
        createdActor = event.getCreated().getActor();
      }

      try {
        edgesToAdd.add(
            new Edge(
                urn,
                Urn.createFromString(fieldValue.toString()),
                extractedFieldsEntry.getKey().getRelationshipName(),
                createdOn,
                createdActor,
                updatedOn,
                updatedActor,
                properties
            )
        );
      } catch (URISyntaxException e) {
        log.error("Invalid destination urn: {}", fieldValue.toString(), e);
      }
      index++;
    }
    return edgesToAdd;
  }
}
