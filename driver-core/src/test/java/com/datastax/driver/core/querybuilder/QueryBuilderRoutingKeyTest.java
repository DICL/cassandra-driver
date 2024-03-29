/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core.querybuilder;

import com.datastax.driver.core.*;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class QueryBuilderRoutingKeyTest extends CCMBridge.PerClassSingleNodeCluster {

    private static final String TABLE_TEXT = "test_text";
    private static final String TABLE_INT = "test_int";

    @Override
    protected Collection<String> getTableDefinitions() {
        return Arrays.asList(String.format("CREATE TABLE %s (k text PRIMARY KEY, a int, b int)", TABLE_TEXT),
                String.format("CREATE TABLE %s (k int PRIMARY KEY, a int, b int)", TABLE_INT));
    }

    @Override
    protected Cluster.Builder configure(Cluster.Builder builder) {
        return builder.withQueryOptions(new QueryOptions()
                        .setRefreshNodeIntervalMillis(0)
                        .setRefreshNodeListIntervalMillis(0)
                        .setRefreshSchemaIntervalMillis(0)
        );
    }

    @Test(groups = "short")
    public void textRoutingKeyTest() throws Exception {

        BuiltStatement query;
        TableMetadata table = cluster.getMetadata().getKeyspace(keyspace).getTable(TABLE_TEXT);
        assertNotNull(table);
        ProtocolVersion protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
        CodecRegistry codecRegistry = CodecRegistry.DEFAULT_INSTANCE;

        String txt = "If she weighs the same as a duck... she's made of wood.";
        query = insertInto(table).values(new String[]{"k", "a", "b"}, new Object[]{txt, 1, 2});
        assertEquals(query.getRoutingKey(protocolVersion, codecRegistry), ByteBuffer.wrap(txt.getBytes()));
        session.execute(query);

        query = select().from(table).where(eq("k", txt));
        assertEquals(query.getRoutingKey(protocolVersion, codecRegistry), ByteBuffer.wrap(txt.getBytes()));
        Row row = session.execute(query).one();
        assertEquals(row.getString("k"), txt);
        assertEquals(row.getInt("a"), 1);
        assertEquals(row.getInt("b"), 2);
    }

    @Test(groups = "short")
    public void intRoutingKeyTest() throws Exception {

        BuiltStatement query;
        TableMetadata table = cluster.getMetadata().getKeyspace(keyspace).getTable(TABLE_INT);
        assertNotNull(table);
        ProtocolVersion protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
        CodecRegistry codecRegistry = CodecRegistry.DEFAULT_INSTANCE;

        query = insertInto(table).values(new String[]{"k", "a", "b"}, new Object[]{42, 1, 2});
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(0, 42);
        assertEquals(query.getRoutingKey(protocolVersion, codecRegistry), bb);
        session.execute(query);

        query = select().from(table).where(eq("k", 42));
        assertEquals(query.getRoutingKey(protocolVersion, codecRegistry), bb);
        Row row = session.execute(query).one();
        assertEquals(row.getInt("k"), 42);
        assertEquals(row.getInt("a"), 1);
        assertEquals(row.getInt("b"), 2);
    }

    @Test(groups = "short")
    public void intRoutingBatchKeyTest() throws Exception {

        BuiltStatement query;
        TableMetadata table = cluster.getMetadata().getKeyspace(keyspace).getTable(TABLE_INT);
        assertNotNull(table);
        ProtocolVersion protocolVersion = cluster.getConfiguration().getProtocolOptions().getProtocolVersion();
        CodecRegistry codecRegistry = CodecRegistry.DEFAULT_INSTANCE;

        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(0, 42);

        String batch_query;
        BuiltStatement batch;

        query = select().from(table).where(eq("k", 42));

        batch_query = "BEGIN BATCH ";
        batch_query += String.format("INSERT INTO %s.test_int (k,a) VALUES (42,1);", keyspace);
        batch_query += String.format("UPDATE %s.test_int USING TTL 400;", keyspace);
        batch_query += "APPLY BATCH;";
        batch = batch()
                .add(insertInto(table).values(new String[]{"k", "a"}, new Object[]{42, 1}))
                .add(update(table).using(ttl(400)));
        assertEquals(batch.getRoutingKey(protocolVersion, codecRegistry), bb);
        assertEquals(batch.toString(), batch_query);
        // TODO: rs = session.execute(batch); // Not guaranteed to be valid CQL

        batch_query = "BEGIN BATCH ";
        batch_query += String.format("SELECT * FROM %s.test_int WHERE k=42;", keyspace);
        batch_query += "APPLY BATCH;";
        batch = batch(query);
        assertEquals(batch.getRoutingKey(protocolVersion, codecRegistry), bb);
        assertEquals(batch.toString(), batch_query);
        // TODO: rs = session.execute(batch); // Not guaranteed to be valid CQL

        batch_query = "BEGIN BATCH ";
        batch_query += "SELECT * FROM foo WHERE k=42;";
        batch_query += "APPLY BATCH;";
        batch = batch().add(select().from("foo").where(eq("k", 42)));
        assertEquals(batch.getRoutingKey(protocolVersion, codecRegistry), null);
        assertEquals(batch.toString(), batch_query);
        // TODO: rs = session.execute(batch); // Not guaranteed to be valid CQL

        batch_query = "BEGIN BATCH USING TIMESTAMP 42 ";
        batch_query += "INSERT INTO foo.bar (a) VALUES (123);";
        batch_query += "APPLY BATCH;";
        batch = batch().using(timestamp(42)).add(insertInto("foo", "bar").value("a", 123));
        assertEquals(batch.getRoutingKey(protocolVersion, codecRegistry), null);
        assertEquals(batch.toString(), batch_query);
        // TODO: rs = session.execute(batch); // Not guaranteed to be valid CQL
    }
}
