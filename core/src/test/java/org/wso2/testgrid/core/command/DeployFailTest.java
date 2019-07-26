package org.wso2.testgrid.core.command;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.testgrid.automation.executor.TestExecutorFactory;
import org.wso2.testgrid.common.util.StringUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertTrue;

@PrepareForTest({ StringUtil.class, TestExecutorFactory.class })
@PowerMockIgnore({ "javax.management.*", "javax.script.*", "org.apache.logging.log4j.*" })
public class DeployFailTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(RunTestPlanCommandTest.class);
    private static final String TESTGRID_HOME = Paths.get("target", "testgrid-home").toString();
    private static final String infraParamsString = "{\"operating_system\":\"ubuntu_16.04\"}";
    private static final String TESTPLAN_ID = "TP_1";

    private static final String workspaceDir = Paths.get("target", "test-classes", "multi-deploy-script-test",
            "workspace").toString();

    public DeployFailTest() {
        super(workspaceDir);
    }

    @Test
    public void testExecute() throws Exception {
        String jobConfigFile =
                Paths.get("src", "test", "resources", "multi-deploy-script-test", "workspace", "job-config.yaml")
                        .toString();
        GenerateTestPlanCommand generateTestPlanCommand = new GenerateTestPlanCommand(product.getName(),
                jobConfigFile, infrastructureCombinationsProvider, productUOW,
                deploymentPatternUOW, testPlanUOW);
        generateTestPlanCommand.execute();

        Path actualTestPlanPath = Paths.get(actualTestPlanFileLocation);
        assertTrue(Files.exists(actualTestPlanPath));

        try {
            runTestPlanCommand.execute();
            Assert.fail("Run test plan command has not exited even though deploy step fails.");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("DEPLOY_PHASE_ERROR"), "Exception message need to contain "
                    + "text: DEPLOY_PHASE_ERROR");
            Assert.assertTrue(Files.exists(Paths.get(workspaceDir, "deployment", "file1.txt")));
            Assert.assertFalse(Files.exists(Paths.get(workspaceDir, "deployment", "file-last.txt")));
        }
    }

}
