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
package org.apache.pulsar.admin.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.StringConverter;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarFunctionsAdmin;
import org.apache.pulsar.functions.fs.FunctionConfig;
import org.apache.pulsar.functions.runtime.spawner.LimitsConfig;
import org.apache.pulsar.functions.runtime.spawner.Spawner;

import java.nio.file.Files;
import java.nio.file.Paths;

@Parameters(commandDescription = "Operations about functions")
public class CmdFunctions extends CmdBase {
    private LocalRunner localRunner;
    private SubmitFunction submitter;
    @Getter
    abstract class FunctionsCommand extends CliCommand {
        @Parameter(names = "--name", description = "Function Name\n")
        protected String name;
        @Parameter(names = "--function-classname", description = "Function Class Name\n")
        protected String className;
        @Parameter(
                names = "--function-classpath",
                description = "Function Classpath\n",
                listConverter = StringConverter.class)
        protected String jarFile;
        @Parameter(names = "--source-topic", description = "Input Topic Name\n")
        protected String sourceTopicName;
        @Parameter(names = "--sink-topic", description = "Output Topic Name\n")
        protected String sinkTopicName;

        @Parameter(names = "--input-serde-classname", description = "Input SerDe\n")
        protected String inputSerdeClassName;

        @Parameter(names = "--output-serde-classname", description = "Output SerDe\n")
        protected String outputSerdeClassName;

        @Parameter(names = "--function-config", description = "Function Config\n")
        protected String fnConfigFile;
        protected FunctionConfig functionConfig;

        @Override
        void run() throws Exception {
            if (null != fnConfigFile) {
                functionConfig = FunctionConfig.load(fnConfigFile);
            } else {
                functionConfig = new FunctionConfig();
            }
            if (null != sourceTopicName) {
                functionConfig.setSourceTopic(sourceTopicName);
            }
            if (null != sinkTopicName) {
                functionConfig.setSinkTopic(sinkTopicName);
            }
            if (null != name) {
                functionConfig.setName(name);
            }
            if (null != className) {
                functionConfig.setClassName(className);
            }
            if (null != inputSerdeClassName) {
                functionConfig.setInputSerdeClassName(inputSerdeClassName);
            }
            if (null != outputSerdeClassName) {
                functionConfig.setOutputSerdeClassName(outputSerdeClassName);
            }

            run_functions_cmd();
        }

        abstract void run_functions_cmd() throws Exception;
    }

    @Parameters(commandDescription = "Run function locally")
    class LocalRunner extends FunctionsCommand {

        @Override
        void run_functions_cmd() throws Exception {
            LimitsConfig limitsConfig = new LimitsConfig(
                60000,   // 60 seconds
                1024,       // 1GB
                1024   // 1024 outstanding tuples
            );

            Spawner spawner = Spawner.createSpawner(
                functionConfig,
                limitsConfig,
                admin.getServiceUrl().toString(), jarFile);

            spawner.start();
            spawner.join();
        }
    }

    @Parameters(commandDescription = "Submit function")
    class SubmitFunction extends FunctionsCommand {
        @Override
        void run_functions_cmd() throws Exception {
            PulsarFunctionsAdmin a = (PulsarFunctionsAdmin)admin;
            a.functions().createFunction(functionConfig, Files.readAllBytes(Paths.get(jarFile)));
        }
    }

    public CmdFunctions(PulsarAdmin admin) {
        super("functions", admin);
        localRunner = new LocalRunner();
        submitter = new SubmitFunction();
        jcommander.addCommand("localrun", localRunner);
        jcommander.addCommand("submit", submitter);
    }

    @VisibleForTesting
    LocalRunner getLocalRunner() {
        return localRunner;
    }

    @VisibleForTesting
    SubmitFunction getSubmitter() {
        return submitter;
    }
}
