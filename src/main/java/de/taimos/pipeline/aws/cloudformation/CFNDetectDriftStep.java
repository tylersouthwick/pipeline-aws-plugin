/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class CFNDetectDriftStep extends Step {

	private String stack;
	private PollConfiguration pollConfiguration = PollConfiguration.DEFAULT;

	@DataBoundConstructor
	public CFNDetectDriftStep() {
		//
	}

	public String getStack() {
		return this.stack;
	}

	@DataBoundSetter
	public void setStack(String stack) {
		this.stack = stack;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNDetectDriftStep.Execution(this, context);
	}

	public PollConfiguration getPollConfiguration() {
		return this.pollConfiguration;
	}

	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.pollInterval(Duration.ofMillis(pollInterval))
				.build();
	}

	@DataBoundSetter
	public void setTimeoutInSeconds(long timeout) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.timeout(Duration.ofSeconds(timeout))
				.build();
	}

	@DataBoundSetter
	public void setTimeoutInMinutes(long timeout) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.timeout(Duration.ofMinutes(timeout))
				.build();
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "cfnDetectDrift";
		}

		@Override
		public String getDisplayName() {
			return "Detect CloudFormation Stack Drift";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		private final transient CFNDetectDriftStep step;

		public Execution(CFNDetectDriftStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Map<String, String> run() throws Exception {
			TaskListener taskListener =this.getContext().get(TaskListener.class);
			EnvVars envVars = this.getContext().get(EnvVars.class);
			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), envVars);
			CloudFormationStack stack = new CloudFormationStack(client, this.step.getStack(), taskListener);

			stack.findStackDrift(this.step.getPollConfiguration());
			return Collections.emptyMap();
		}


		private static final long serialVersionUID = 1L;

	}

}
