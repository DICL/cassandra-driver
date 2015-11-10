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
package com.datastax.driver.graph;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.ImmutableMap;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;

/**
 * The GraphSession object allows to execute and prepare graph specific statements.
 *
 * You should use this object whenever the statement to execute is a graph query.
 * The object will make verifications before executing queries to make sure the
 * statement sent is valid.
 * This object also generates graph result sets.
 */
public class GraphSession {
    private final Session session;
    private final ConcurrentMap<String, ByteBuffer> defaultGraphPayload;

    // Static keys for the Custom Payload map
    final static String GRAPH_SOURCE_KEY;
    final static String GRAPH_LANGUAGE_KEY;
    final static String GRAPH_KEYPSACE_KEY;
    final static String GRAPH_REBINDING_KEY;

    // Add static DefaultPayload values for Graph
    final static String DEFAULT_GRAPH_LANGUAGE;
    final static String DEFAULT_GRAPH_SOURCE;
    final static Map<String, ByteBuffer> DEFAULT_GRAPH_PAYLOAD;

    static {
        GRAPH_SOURCE_KEY        = "graph-source";
        GRAPH_LANGUAGE_KEY      = "graph-language";
        GRAPH_KEYPSACE_KEY      = "graph-keyspace";
        GRAPH_REBINDING_KEY     = "graph-rebinding";


        DEFAULT_GRAPH_LANGUAGE  = "gremlin-groovy";
        DEFAULT_GRAPH_SOURCE    = "default";
        DEFAULT_GRAPH_PAYLOAD   = ImmutableMap.of(
            // For the first versions of the driver Gremlin-Groovy is the default language
            GRAPH_LANGUAGE_KEY, ByteBuffer.wrap(DEFAULT_GRAPH_LANGUAGE.getBytes()),

            //If not present, the default source configured for the Keyspace
            GRAPH_SOURCE_KEY, ByteBuffer.wrap(DEFAULT_GRAPH_SOURCE.getBytes())
        );
    }

    /**
     * API
     */

    /**
     * Create a GraphSession object that wraps an underlying Java Driver Session.
     * The Java Driver session have to be initialised.
     *
     * @param session the session to wrap, basically the return of a cluster.connect() call.
     */
    public GraphSession(Session session) {
        this.session = session;
        this.defaultGraphPayload = new ConcurrentHashMap<String, ByteBuffer>(DEFAULT_GRAPH_PAYLOAD);
    }

    /**
     * Execute a graph statement.
     *
     * @param statement Any inherited class of {@link com.datastax.driver.graph.AbstractGraphStatement}. For now,
     *                  this covers execution of simple and bound graph statements. It is extensible in case
     *                  users would need to implement their own way {@link com.datastax.driver.graph.AbstractGraphStatement}.
     * @return A {@link GraphResultSet} object if the execution of the query was successful. This method can throw an exception
     * in case the execution did non complete successfully.
     */
    public GraphResultSet execute(AbstractGraphStatement statement) {
        if (!AbstractGraphStatement.checkStatement(statement)) {
            throw new InvalidQueryException("Invalid Graph Statement, you need to specify at least the keyspace containing the Graph data.");
        }
        return new GraphResultSet(session.execute(statement.configureAndGetWrappedStatement()));
    }

    /**
     * Execute a graph query.
     *
     * @param query This method will create a {@link GraphStatement} object internally, to be executed.
     *              The graph options included in this generated {@link GraphStatement} will be the default options set in the {@link GraphStatement}.
     * @return A {@link GraphResultSet} object if the execution of the query was successful. This method can throw an exception
     * in case the execution did non complete successfully.
     */
    public GraphResultSet execute(String query) {
        GraphStatement statement = newGraphStatement(query);
        return execute(statement);
    }

    /**
     * Prepare a graph statement.
     *
     * @param gst A simple {@link GraphStatement} object to be prepared.
     *
     * @return A {@link PreparedGraphStatement} object to be bound with values, and executed.
     */
//    public PreparedGraphStatement prepare(GraphStatement gst) {
//        return new PreparedGraphStatement(session.prepare(gst.configureAndGetWrappedStatement()), gst);
//    }

    /**
     * Prepare a graph statement.
     *
     * @param query This method will create a {@link GraphStatement} object internally, to be executed.
     *              The graph options included in this generated {@link GraphStatement} will be the default options set in the {@link GraphSession}.
     * @return A {@link PreparedGraphStatement} object to be bound with values, and executed.
     */
//    public PreparedGraphStatement prepare(String query) {
//        GraphStatement statement = newGraphStatement(query);
//        return prepare(statement);
//    }

    /**
     * Get the wrapped {@link com.datastax.driver.core.Session}.
     * @return the wrapped {@link com.datastax.driver.core.Session}.
     */
    public Session getSession() {
        return this.session;
    }

    /**
     * Create a new {@link com.datastax.driver.graph.GraphStatement} with the default options
     * set in the {@link com.datastax.driver.graph.GraphSession}.
     *
     * @param query The graph query.
     *
     * @return A {@link com.datastax.driver.graph.GraphStatement} object to be executed in the session.
     */
    public GraphStatement newGraphStatement(String query) {
        return new GraphStatement(query, this);
    }

    /**
     * Get the default graph options configured for this session.
     *
     * @return A Map of the options, encoded in bytes in a ByteBuffer.
     */
    public Map<String, ByteBuffer> getDefaultGraphOptions() {
        return this.defaultGraphPayload;
    }

    /**
     * Reset the default graph options for this {@link com.datastax.driver.graph.GraphSession}.
     *
     */
    public boolean resetDefaultGraphOptions() {
        this.defaultGraphPayload.clear();
        this.defaultGraphPayload.putAll(DEFAULT_GRAPH_PAYLOAD);
        // Just a quick verification
        return this.defaultGraphPayload.hashCode() == DEFAULT_GRAPH_PAYLOAD.hashCode();
    }

    /**
     * Set the default Graph traversal source name on the graph side.
     *
     * The default value for this property is "default".
     *
     * @param input The graph traversal source's name to use.
     * @return This {@link com.datastax.driver.graph.GraphSession} instance to chain call.
     */
    public GraphSession setDefaultGraphSource(String input) {
        this.defaultGraphPayload.put(GRAPH_SOURCE_KEY, ByteBuffer.wrap(input.getBytes()));
        return this;
    }

    /**
     * Set the default Graph language to use in the query.
     * <p/>
     * The default value for this property is "gremlin-groovy".
     *
     * @param input The language used in queries.
     * @return This {@link com.datastax.driver.graph.GraphSession} instance to chain call.
     */
    public GraphSession setDefaultGraphLanguage(String input) {
        this.defaultGraphPayload.put(GRAPH_LANGUAGE_KEY, ByteBuffer.wrap(input.getBytes()));
        return this;
    }

    /**
     * Set the default Cassandra keyspace name storing the graph.
     *
     * @param input The Cassandra keyspace name to use.
     * @return This {@link com.datastax.driver.graph.GraphSession} instance to chain call.
     */
    public GraphSession setDefaultGraphKeyspace(String input) {
        this.defaultGraphPayload.put(GRAPH_KEYPSACE_KEY, ByteBuffer.wrap(input.getBytes()));
        return this;
    }

    /**
     * Set the default Graph rebinding name to use.
     * <p/>
     * The default value for this property is "default".
     *
     * @param input The graph traversal source's name to use.
     * @return This {@link com.datastax.driver.graph.GraphSession} instance to chain call.
     */
    public GraphSession setDefaultGraphRebinding(String input) {
        this.defaultGraphPayload.put(GRAPH_REBINDING_KEY, ByteBuffer.wrap(input.getBytes()));
        return this;
    }
}