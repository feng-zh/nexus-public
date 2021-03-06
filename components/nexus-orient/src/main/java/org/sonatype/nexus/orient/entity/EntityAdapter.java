/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.orient.entity;

import java.lang.reflect.ParameterizedType;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.Entity;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.orient.RecordIdObfuscator;

import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.hook.ORecordHook.TYPE;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.record.impl.ODocument;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.common.entity.EntityHelper.id;

/**
 * Support for entity-adapter implementations.
 *
 * @since 3.0
 */
public abstract class EntityAdapter<T extends Entity>
    extends ComponentSupport
{
  private final String typeName;

  private final Class<T> entityType;

  private RecordIdObfuscator recordIdObfuscator;

  private OClass schemaType;

  @SuppressWarnings("unchecked")
  public EntityAdapter(final String typeName) {
    this.typeName = checkNotNull(typeName);

    // this extracts the concrete entity type from the generic signature of any sub-classes
    final TypeToken<?> superType = TypeToken.of(getClass()).getSupertype(EntityAdapter.class);
    entityType = (Class<T>) ((ParameterizedType) superType.getType()).getActualTypeArguments()[0];
  }

  public String getTypeName() {
    return typeName;
  }

  public Class<T> getEntityType() {
    return entityType;
  }

  @Inject
  public void installDependencies(final RecordIdObfuscator recordIdObfuscator) {
    this.recordIdObfuscator = checkNotNull(recordIdObfuscator);
  }

  protected RecordIdObfuscator getRecordIdObfuscator() {
    checkState(recordIdObfuscator != null);
    return recordIdObfuscator;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
        "typeName='" + typeName + '\'' +
        '}';
  }

  //
  // Schema
  //

  public void register(final ODatabaseDocumentTx db, @Nullable final Runnable initalizer) {
    checkNotNull(db);

    OSchema schema = db.getMetadata().getSchema();
    OClass type = schema.getClass(typeName);
    if (type == null) {
      type = schema.createClass(typeName);
      defineType(db, type);

      log.debug("Created schema type '{}': properties={}, indexes={}",
          type,
          type.properties(),
          type.getIndexes()
      );

      if (initalizer != null) {
        log.debug("Running initializer: {}", initalizer);
        initalizer.run();
      }
    }
    this.schemaType = type;
  }

  public void register(final ODatabaseDocumentTx db) {
    register(db, null);
  }

  public boolean isRegistered(final ODatabaseDocumentTx db) {
    return db.getMetadata().getSchema().existsClass(typeName);
  }

  protected void defineType(final ODatabaseDocumentTx db, final OClass type) {
    defineType(type);
  }

  protected abstract void defineType(final OClass type);

  public OClass getSchemaType() {
    checkState(schemaType != null, "Not registered");
    return schemaType;
  }

  //
  // BREAD operations
  //

  protected abstract T newEntity();

  protected abstract void readFields(final ODocument document, final T entity) throws Exception;

  protected abstract void writeFields(final ODocument document, final T entity) throws Exception;

  /**
   * Browse all documents.
   */
  protected Iterable<ODocument> browseDocuments(final ODatabaseDocumentTx db) {
    checkNotNull(db);
    return db.browseClass(typeName);
  }

  /**
   * Read entity from document.
   */
  protected T readEntity(final ODocument document) {
    checkNotNull(document);

    T entity = newEntity();
    try {
      readFields(document, entity);
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
    attachMetadata(entity, document);

    return entity;
  }

  /**
   * Write document from entity.
   */
  protected ODocument writeEntity(final ODocument document, final T entity) {
    checkNotNull(document);
    checkNotNull(entity);

    // TODO: MVCC

    try {
      writeFields(document, entity);
    }
    catch (Exception e) {
      throw Throwables.propagate(e);
    }
    attachMetadata(entity, document);

    return document.save();
  }

  /**
   * Edit entity.
   */
  protected ODocument editEntity(final ODatabaseDocumentTx db, final T entity) {
    checkNotNull(db);
    checkNotNull(entity);

    ORID rid = recordIdentity(entity);
    ODocument document = db.getRecord(rid);
    checkState(document != null);

    return writeEntity(document, entity);
  }

  /**
   * Add new entity.
   */
  protected ODocument addEntity(final ODatabaseDocumentTx db, final T entity) {
    checkNotNull(db);
    checkNotNull(entity);

    // new entity must not already have metadata
    checkState(entity.getEntityMetadata() == null);

    ODocument doc = db.newInstance(typeName);
    return writeEntity(doc, entity);
  }

  /**
   * Delete an entity.
   */
  protected void deleteEntity(final ODatabaseDocumentTx db, final T entity) {
    checkNotNull(db);
    checkNotNull(entity);

    // TODO: MVCC

    ORID rid = recordIdentity(entity);
    db.delete(rid);

    entity.setEntityMetadata(null);
  }

  //
  // Metadata support
  //

  /**
   * Attach metadata to entity.
   */
  protected void attachMetadata(final T entity, final ODocument doc) {
    entity.setEntityMetadata(new AttachedEntityMetadata(this, doc));
  }

  /**
   * Return the document for given {@link EntityId}.
   */
  protected ODocument document(final ODatabaseDocumentTx db, final EntityId id) {
    ORID rid;
    if (id instanceof AttachedEntityId) {
      rid = ((AttachedEntityId)id).getIdentity();
    }
    else {
      rid = getRecordIdObfuscator().decode(getSchemaType(), id.getValue());
    }
    return db.getRecord(rid);
  }

  /**
   * Return record identity of entity.
   */
  public ORID recordIdentity(final T entity) {
    return recordIdentity(id(entity));
  }

  public ORID recordIdentity(final EntityId id) {
    checkNotNull(id);
    if (id instanceof AttachedEntityId) {
      return ((AttachedEntityId) id).getIdentity();
    }
    return getRecordIdObfuscator().decode(getSchemaType(), id.getValue());
  }

  //
  // Event support
  //

  /**
   * Override this method to enable {@link EntityEvent}s for this adapter.
   */
  public boolean sendEvents() {
    return false;
  }

  /**
   * Override this method to customize {@link EntityEvent}s for this adapter.
   */
  @Nullable
  public EntityEvent newEvent(final ODocument document, final TYPE eventType) {
    final AttachedEntityMetadata metadata = new AttachedEntityMetadata(this, document);
    switch (eventType) {
      case AFTER_CREATE:
        return new EntityCreatedEvent(metadata);
      case AFTER_UPDATE:
        return new EntityUpdatedEvent(metadata);
      case AFTER_DELETE:
        return new EntityDeletedEvent(metadata);
      default:
        return null;
    }
  }
}
