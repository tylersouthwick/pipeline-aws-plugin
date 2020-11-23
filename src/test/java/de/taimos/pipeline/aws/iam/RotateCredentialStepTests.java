package de.taimos.pipeline.aws.iam;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import de.taimos.pipeline.aws.AWSClientFactory;
import groovy.lang.Closure;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.iam.*"
)
@PowerMockIgnore("javax.crypto.*")
public class RotateCredentialStepTests {

	@Rule
	private final JenkinsRule jenkinsRule = new JenkinsRule();
	private CredentialsRotater credentialsRotater;

	@Before
	public void setupSdk() throws Exception {
		this.credentialsRotater = Mockito.mock(CredentialsRotater.class);
		AmazonIdentityManagement iam = Mockito.mock(AmazonIdentityManagement.class);

		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.whenNew(CredentialsRotater.class)
				.withArguments(Mockito.eq(iam), Mockito.any(TaskListener.class))
				.thenReturn(this.credentialsRotater);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
				.thenReturn(iam);
	}

	@Test
	public void rotateCredentials() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "iamTest");
		Mockito.doAnswer(invocationOnMock -> {
			@SuppressWarnings("unchecked")
			Closure<Void> closure = invocationOnMock.getArgumentAt(1, Closure.class);
			return closure.call("ak", "sk");
		}).when(credentialsRotater).rotateCredentials(Mockito.eq("foo"), Mockito.any());

		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  iamRotateCredentials(username: \"foo\") {\n"
				+ "    echo \"hello world\"\n"
				//+ "    echo \"accesKeyId=${accessKeyId};secretKey=${secretKey}\"\n"
				+ "  }\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

	}
}
