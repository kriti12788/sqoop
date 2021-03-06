/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sqoop.orm;

import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;

import com.cloudera.sqoop.SqoopOptions;
import com.cloudera.sqoop.manager.ConnManager;

/**
 * Creates an Avro schema to represent a table from a database.
 */
public class AvroSchemaGenerator {

  private final SqoopOptions options;
  private final ConnManager connManager;
  private final String tableName;

  public AvroSchemaGenerator(final SqoopOptions opts, final ConnManager connMgr,
      final String table) {
    this.options = opts;
    this.connManager = connMgr;
    this.tableName = table;
  }

  public Schema generate() throws IOException {
    ClassWriter classWriter = new ClassWriter(options, connManager,
        tableName, null);
    Map<String, Integer> columnTypes = classWriter.getColumnTypes();
    String[] columnNames = classWriter.getColumnNames(columnTypes);

    List<Field> fields = new ArrayList<Field>();
    for (String columnName : columnNames) {
      String cleanedCol = ClassWriter.toIdentifier(columnName);
      int sqlType = columnTypes.get(cleanedCol);
      Schema avroSchema = toAvroSchema(sqlType);
      Field field = new Field(cleanedCol, avroSchema, null, null);
      field.addProp("columnName", columnName);
      field.addProp("sqlType", Integer.toString(sqlType));
      fields.add(field);
    }

    String avroTableName = (tableName == null ? "QueryResult" : tableName);

    String doc = "Sqoop import of " + avroTableName;
    Schema schema = Schema.createRecord(avroTableName, doc, null, false);
    schema.setFields(fields);
    schema.addProp("tableName", avroTableName);
    return schema;
  }

  private Type toAvroType(int sqlType) {
    switch (sqlType) {
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
        return Type.INT;
      case Types.BIGINT:
        return Type.LONG;
      case Types.BIT:
      case Types.BOOLEAN:
        return Type.BOOLEAN;
      case Types.REAL:
        return Type.FLOAT;
      case Types.FLOAT:
      case Types.DOUBLE:
        return Type.DOUBLE;
      case Types.NUMERIC:
      case Types.DECIMAL:
        return Type.STRING;
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.LONGNVARCHAR:
      case Types.NVARCHAR:
      case Types.NCHAR:
        return Type.STRING;
      case Types.DATE:
      case Types.TIME:
      case Types.TIMESTAMP:
        return Type.LONG;
      case Types.BINARY:
      case Types.VARBINARY:
        return Type.BYTES;
      default:
        throw new IllegalArgumentException("Cannot convert SQL type "
            + sqlType);
    }
  }

  public Schema toAvroSchema(int sqlType) {
    // All types are assumed nullabl;e make a union of the "true" type for
    // a column and NULL.
    List<Schema> childSchemas = new ArrayList<Schema>();
    childSchemas.add(Schema.create(toAvroType(sqlType)));
    childSchemas.add(Schema.create(Schema.Type.NULL));
    return Schema.createUnion(childSchemas);
  }

}
