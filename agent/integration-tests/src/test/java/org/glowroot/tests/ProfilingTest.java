/*
 * Copyright 2011-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.agent.harness.AppUnderTest;
import org.glowroot.agent.harness.Container;
import org.glowroot.agent.harness.Containers;
import org.glowroot.agent.harness.Threads;
import org.glowroot.agent.harness.TransactionMarker;
import org.glowroot.agent.harness.config.TransactionConfig;
import org.glowroot.agent.harness.config.UserRecordingConfig;
import org.glowroot.agent.harness.trace.ProfileTree;
import org.glowroot.agent.harness.trace.Trace;
import org.glowroot.agent.plugin.api.Agent;
import org.glowroot.agent.plugin.api.transaction.TransactionService;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfilingTest {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
        // capture one header to warm up the system, otherwise sometimes there are delays in class
        // loading and the profiler captures too many or too few samples
        container.executeAppUnderTest(ShouldGenerateTraceWithProfile.class);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void shouldReadProfile() throws Exception {
        // given
        TransactionConfig transactionConfig = container.getConfigService().getTransactionConfig();
        transactionConfig.setProfilingIntervalMillis(20);
        container.getConfigService().updateTransactionConfig(transactionConfig);
        // when
        container.executeAppUnderTest(ShouldGenerateTraceWithProfile.class);
        // then
        Trace.Header header = container.getTraceService().getLastHeader();
        assertThat(header.profileSampleCount()).isGreaterThan(0);
        // profiler should have captured about 10 stack traces
        Thread.sleep(1000);
        ProfileTree profileTree = container.getTraceService().getProfile(header.id());
        assertThat(profileTree.unfilteredSampleCount()).isBetween(5L, 15L);
    }

    @Test
    public void shouldNotReadProfile() throws Exception {
        // given
        TransactionConfig transactionConfig = container.getConfigService().getTransactionConfig();
        transactionConfig.setProfilingIntervalMillis(0);
        container.getConfigService().updateTransactionConfig(transactionConfig);
        // when
        container.executeAppUnderTest(ShouldGenerateTraceWithProfile.class);
        // then
        Trace.Header header = container.getTraceService().getLastHeader();
        assertThat(header.profileSampleCount()).isZero();
    }

    @Test
    public void shouldReadUserRecordingProfile() throws Exception {
        // given
        UserRecordingConfig userRecordingConfig =
                container.getConfigService().getUserRecordingConfig();
        userRecordingConfig.setEnabled(true);
        userRecordingConfig.setUser("able");
        userRecordingConfig.setProfileIntervalMillis(20);
        container.getConfigService().updateUserRecordingConfig(userRecordingConfig);
        TransactionConfig transactionConfig = container.getConfigService().getTransactionConfig();
        transactionConfig.setProfilingIntervalMillis(0);
        container.getConfigService().updateTransactionConfig(transactionConfig);
        // when
        container.executeAppUnderTest(ShouldGenerateTraceWithProfileForAble.class);
        // then
        Trace.Header header = container.getTraceService().getLastHeader();
        assertThat(header.profileSampleCount()).isGreaterThan(0);
        // profiler should have captured about 10 stack traces
        ProfileTree profileTree = container.getTraceService().getProfile(header.id());
        assertThat(profileTree.unfilteredSampleCount()).isBetween(5L, 15L);
    }

    @Test
    public void shouldNotReadUserRecordingProfile() throws Exception {
        // given
        UserRecordingConfig userRecordingConfig =
                container.getConfigService().getUserRecordingConfig();
        userRecordingConfig.setEnabled(true);
        userRecordingConfig.setUser("baker");
        userRecordingConfig.setProfileIntervalMillis(20);
        container.getConfigService().updateUserRecordingConfig(userRecordingConfig);
        TransactionConfig transactionConfig = container.getConfigService().getTransactionConfig();
        transactionConfig.setProfilingIntervalMillis(0);
        container.getConfigService().updateTransactionConfig(transactionConfig);
        // when
        container.executeAppUnderTest(ShouldGenerateTraceWithProfileForAble.class);
        // then
        Trace.Header header = container.getTraceService().getLastHeader();
        assertThat(header.profileSampleCount()).isZero();
    }

    public static class ShouldGenerateTraceWithProfile implements AppUnderTest, TransactionMarker {
        @Override
        public void executeApp() throws InterruptedException {
            transactionMarker();
        }
        @Override
        public void transactionMarker() throws InterruptedException {
            Threads.moreAccurateSleep(200);
        }
    }

    public static class ShouldGenerateTraceWithProfileForAble
            implements AppUnderTest, TransactionMarker {
        private static final TransactionService transactionService = Agent.getTransactionService();
        @Override
        public void executeApp() throws InterruptedException {
            transactionMarker();
        }
        @Override
        public void transactionMarker() throws InterruptedException {
            // normally the plugin/aspect should set the user, this is just a shortcut for test
            transactionService.setTransactionUser("Able");
            Threads.moreAccurateSleep(200);
        }
    }
}