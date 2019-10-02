/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.testgrid.deployment.deployers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.testgrid.common.Deployer;
import org.wso2.testgrid.common.config.ConfigurationContext;
import org.wso2.testgrid.common.DeploymentCreationResult;
import org.wso2.testgrid.common.InfrastructureProvisionResult;
import org.wso2.testgrid.common.TestGridConstants;
import org.wso2.testgrid.common.TestPlan;
import org.wso2.testgrid.common.config.Script;
import org.wso2.testgrid.common.exception.TestGridDeployerException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Map;
import java.util.Properties;
import org.yaml.snakeyaml.*;

/**
 * This class performs Kubernetes related deployment tasks using helm. This class is used to deploy
 * the helm deployer script which is used to deploy the deployments and services
 * in the kubernetes engine using helm charts.
 *
 * @since 1.0.0
 */
public class HelmDeployer implements Deployer {

    private static final String DEPLOYER_NAME = TestPlan.DeployerType.HELM.toString();
    private static final Logger logger = LoggerFactory.getLogger(HelmDeployer.class);

    @Override
    public String getDeployerName() {
        return DEPLOYER_NAME;
    }

    /**
     * This class is used to invoke the script to deploy the deployments in Kubernetes Engine using helm
     *
     * @param testPlan current testPlan configurations
     * @param infrastructureProvisionResult infrastructure provisioning output
     * @return
     * @throws TestGridDeployerException
     */
    @Override
    public DeploymentCreationResult deploy(TestPlan testPlan,
                                           InfrastructureProvisionResult infrastructureProvisionResult,
                                           Script script)
            throws TestGridDeployerException {

        if (script.getInputParameters().containsKey(TestGridConstants.HELM_ESENDPOINT_PARAM_NAME) &&
                script.getInputParameters().containsKey(TestGridConstants.HELM_VALUES_YAML_PARAM_NAME)) {
            editESEndPoint(script.getInputParameters().get(TestGridConstants.HELM_ESENDPOINT_PARAM_NAME).toString()
                    , ConfigurationContext.getProperty(ConfigurationContext.ConfigurationProperties.
                            ELASTICSEARCH_ENDPOINT), testPlan, script.getInputParameters());
        } else {
            logger.info("Elastic Search Parameters not set cannot edit values.yaml");
        }
        String deployRepositoryLocation = Paths.get(testPlan.getDeploymentRepository()).toString();

        InputStream resourceFileStream = getClass().getClassLoader()
                .getResourceAsStream(TestGridConstants.HELM_DEPLOY_SCRIPT);
        try {
            Files.copy(resourceFileStream, Paths.get(testPlan.getDeploymentRepository(),
                    TestGridConstants.HELM_DEPLOY_SCRIPT));
        } catch (IOException e) {
            logger.error("IO error occurred while reading " +
                    TestGridConstants.HELM_DEPLOY_SCRIPT, e);
        }

        DeploymentCreationResult deploymentCreationResult = ShellDeployerFactory.deploy(testPlan,
                infrastructureProvisionResult,
                Paths.get(deployRepositoryLocation, TestGridConstants.HELM_DEPLOY_SCRIPT));

        return deploymentCreationResult;
    }

    private void editESEndPoint(String epPropName, String endPointVal, TestPlan testPlan, Map scriptParams){
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream(testPlan.getDeploymentRepository().concat(scriptParams.get("valuesYaml").toString()));
        Map<String, Object> valuesYaml = yaml.load(inputStream);

    }
}
