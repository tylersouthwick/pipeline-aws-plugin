package de.taimos.pipeline.aws.iam;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import groovy.lang.Closure;
import hudson.Extension;
import hudson.model.TaskListener;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

public class RotateCredentialsStep extends Step {
	@Getter
	String username;

	@DataBoundConstructor
	public RotateCredentialsStep(String username) {
		this.username = username;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new RotateCredentialsStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "iamRotateCredentials";
		}

		@Override
		public String getDisplayName() {
			return "Rotate IAM Credentials";
		}
		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Object> {

		protected static final long serialVersionUID = 1L;

		protected final transient RotateCredentialsStep step;

		public Execution(RotateCredentialsStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public Object run() throws Exception {

			AmazonIdentityManagement client = AWSClientFactory.create(AmazonIdentityManagementClientBuilder.standard(), this.getContext());

			return getContext().newBodyInvoker()
					.start()
					.get();
			//CredentialsRotater rotater = new CredentialsRotater(client, this.getContext().get(TaskListener.class));
			//rotater.rotateCredentials(this.step.getUsername(), this.step.getHandler());
		}

	}

}
