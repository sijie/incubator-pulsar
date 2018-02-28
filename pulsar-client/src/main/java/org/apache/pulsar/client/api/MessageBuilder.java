/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.api;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.pulsar.client.impl.MessageBuilderImpl;

/**
 * Message builder factory. Use this class to create messages to be send to the Pulsar producer
 *
 */
public interface MessageBuilder<T> {
    /**
     * Create a new message builder instance.
     * <p>
     * A message builder is suitable for creating one single message
     *
     * @return a new message builder
     */
    static <T> MessageBuilder<T> create(Schema<T> schema) {
        return new MessageBuilderImpl<>(schema);
    }

    static MessageBuilder<byte[]> create() {
        return create(Schema.IDENTITY);
    }

    /**
     * Finalize the immutable message
     *
     * @return a {@link Message} ready to be sent through a {@link Producer}
     */
    Message<T> build();

    /**
     * Set a domain object on the message
     *
     * @param value
     *            the domain object
     */
    MessageBuilder<T> setValue(T value);

    /**
     * Set the content of the message
     *
     * @param data
     *            array containing the payload
     */
    MessageBuilder<T> setContent(byte[] data);

    /**
     * Set the content of the message
     *
     * @param data
     *            array containing the payload
     * @param offset
     *            offset into the data array
     * @param length
     *            length of the payload starting from the above offset
     */
    MessageBuilder<T> setContent(byte[] data, int offset, int length);

    /**
     * Set the content of the message
     *
     * @param buf
     *            a {@link ByteBuffer} with the payload of the message
     */
    MessageBuilder<T> setContent(ByteBuffer buf);

    /**
     * Sets a new property on a message.
     *
     * @param name
     *            the name of the property
     * @param value
     *            the associated value
     */
    MessageBuilder<T> setProperty(String name, String value);

    /**
     * Add all the properties in the provided map
     */
    MessageBuilder<T> setProperties(Map<String, String> properties);

    /**
     * Sets the key of the message for routing policy
     *
     * @param key
     */
    MessageBuilder<T> setKey(String key);

    /**
     * Set the event time for a given message.
     *
     * <p>Applications can retrieve the event time by calling {@link Message#getEventTime()}.
     *
     * <p>Note: currently pulsar doesn't support event-time based index. so the subscribers can't
     * seek the messages by event time.
     *
     * @since 1.20.0
     */
    MessageBuilder<T> setEventTime(long timestamp);

    /**
     * Specify a custom sequence id for the message being published.
     * <p>
     * The sequence id can be used for deduplication purposes and it needs to follow these rules:
     * <ol>
     * <li><code>sequenceId >= 0</code>
     * <li>Sequence id for a message needs to be greater than sequence id for earlier messages:
     * <code>sequenceId(N+1) > sequenceId(N)</code>
     * <li>It's not necessary for sequence ids to be consecutive. There can be holes between messages. Eg. the
     * <code>sequenceId</code> could represent an offset or a cumulative size.
     * </ol>
     *
     * @param sequenceId
     *            the sequence id to assign to the current message
     * @since 1.20.0
     */
    MessageBuilder<T> setSequenceId(long sequenceId);

    /**
     * Override the replication clusters for this message.
     *
     * @param clusters
     */
    MessageBuilder<T> setReplicationClusters(List<String> clusters);

    /**
     * Disable replication for this message.
     */
    MessageBuilder<T> disableReplication();
}
