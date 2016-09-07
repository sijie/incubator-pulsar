/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.pulsar.discovery.service;

import static com.yahoo.pulsar.discovery.service.ZookeeperCacheLoader.LOADBALANCE_BROKERS_ROOT;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.beust.jcommander.internal.Lists;
import com.yahoo.pulsar.common.policies.data.loadbalancer.LoadReport;
import com.yahoo.pulsar.zookeeper.MockedZooKeeperClientFactoryImpl;
import com.yahoo.pulsar.zookeeper.ZooKeeperClientFactory;

public class ZookeeperCacheLoaderTest {

    private ZooKeeperClientFactory mockZookKeeperFactory;

    @BeforeMethod
    void setup() throws Exception {
        mockZookKeeperFactory = new MockedZooKeeperClientFactoryImpl();
    }

    @AfterMethod
    void teardown() throws Exception {
    }

    /**
     * Create znode for available broker in ZooKeeper and updates it again to verify ZooKeeper cache update
     *
     * @throws InterruptedException
     * @throws KeeperException
     * @throws IOException
     */
    @Test
    public void testZookeeperCacheLoader() throws InterruptedException, KeeperException, Exception {
        ZookeeperCacheLoader zkLoader = new ZookeeperCacheLoader(mockZookKeeperFactory, "");

        List<String> brokers = Lists.newArrayList("broker-1:15000", "broker-2:15000", "broker-3:15000");
        // 1. create znode for each broker
        brokers.stream().forEach(b -> {
            try {
                zkLoader.getLocalZkCache().getZooKeeper().create(LOADBALANCE_BROKERS_ROOT + "/" + b, new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (KeeperException | InterruptedException e) {
                fail("failed while creating broker znodes");
            }
        });

        Thread.sleep(100); // wait for 100 msec: to get cache updated

        // 2. get available brokers from ZookeeperCacheLoader
        List<LoadReport> list = zkLoader.getAvailableBrokers();

        // 3. verify retrieved broker list
        Assert.assertTrue(brokers.containsAll(list));

        // 4.a add new broker
        zkLoader.getLocalZkCache().getZooKeeper().create(LOADBALANCE_BROKERS_ROOT + "/" + "broker-4:15000", new byte[0],
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        brokers.add("broker-4:15000");

        Thread.sleep(100); // wait for 100 msec: to get cache updated

        // 4.b. get available brokers from ZookeeperCacheLoader
        list = zkLoader.getAvailableBrokers();

        // 4.c. verify retrieved broker list
        Assert.assertTrue(brokers.containsAll(list));

    }

}
