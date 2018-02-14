/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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
import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.waiters.Waiter;
import hudson.model.TaskListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CloudFormationStack {

	private final AmazonCloudFormation client;
	private final String stack;
	private final TaskListener listener;

	public CloudFormationStack(AmazonCloudFormation client, String stack, TaskListener listener) {
		this.client = client;
		this.stack = stack;
		this.listener = listener;
	}

	public boolean exists() {
		try {
			DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
			return !result.getStacks().isEmpty();
		} catch (AmazonCloudFormationException e) {
			if ("AccessDenied" .equals(e.getErrorCode())) {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.getErrorMessage());
				throw e;
			} else if ("ValidationError" .equals(e.getErrorCode()) && e.getErrorMessage().contains("does not exist")) {
				return false;
			} else {
				throw e;
			}
		}
	}

	public boolean changeSetExists(String changeSetName) {
		try {
			this.client.describeChangeSet(new DescribeChangeSetRequest().withStackName(this.stack).withChangeSetName(changeSetName));
			return true;
		} catch (AmazonCloudFormationException e) {
			if ("AccessDenied" .equals(e.getErrorCode())) {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.getErrorMessage());
				throw e;
			}
			return false;
		}
	}

	private boolean changeSetHasChanges(String changeSetName) {
		DescribeChangeSetResult result = this.client.describeChangeSet(new DescribeChangeSetRequest().withStackName(this.stack).withChangeSetName(changeSetName));
		return !result.getChanges().isEmpty();
	}

	public Map<String, String> describeOutputs() {
		DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
		Stack cfnStack = result.getStacks().get(0);
		Map<String, String> map = new HashMap<>();
		for (Output output : cfnStack.getOutputs()) {
			map.put(output.getOutputKey(), output.getOutputValue());
		}
		return map;
	}

	public void create(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, Integer timeoutInMinutes, long pollIntervallMillis, String roleArn, String onFailure) throws ExecutionException {
		if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
			throw new IllegalArgumentException("Either a file or url for the template must be specified");
		}

		CreateStackRequest req = new CreateStackRequest();
		req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM);
		req.withTemplateBody(templateBody).withTemplateURL(templateUrl).withParameters(params).withTags(tags).withTimeoutInMinutes(timeoutInMinutes).withRoleARN(roleArn).withOnFailure(OnFailure.valueOf(onFailure));
		this.client.createStack(req);

		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackCreateComplete(), pollIntervallMillis);
	}

	public void update(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, long pollIntervallMillis, String roleArn) throws ExecutionException {
		try {
			UpdateStackRequest req = new UpdateStackRequest();
			req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM);

			if (templateBody != null && !templateBody.isEmpty()) {
				req.setTemplateBody(templateBody);
			} else if (templateUrl != null && !templateUrl.isEmpty()) {
				req.setTemplateURL(templateUrl);
			} else {
				req.setUsePreviousTemplate(true);
			}

			req.withParameters(params).withTags(tags).withRoleARN(roleArn);

			this.client.updateStack(req);

			new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackUpdateComplete(), pollIntervallMillis);

			this.listener.getLogger().format("Updated CloudFormation stack %s %n", this.stack);

		} catch (AmazonCloudFormationException e) {
			if (e.getMessage().contains("No updates are to be performed")) {
				this.listener.getLogger().format("No updates were needed for CloudFormation stack %s %n", this.stack);
				return;
			}
			this.listener.getLogger().format("Failed to update CloudFormation stack %s %n", this.stack);
			throw e;
		}
	}

	public void createChangeSet(String changeSetName, String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, long pollIntervallMillis, ChangeSetType changeSetType, String roleArn) throws ExecutionException {
		try {
			CreateChangeSetRequest req = new CreateChangeSetRequest();
			req.withChangeSetName(changeSetName).withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM).withChangeSetType(changeSetType);

			if (ChangeSetType.CREATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for new stack %s %n", changeSetName, this.stack);
				if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
					throw new IllegalArgumentException("Either a file or url for the template must be specified");
				}
				req.withTemplateBody(templateBody).withTemplateURL(templateUrl);
			} else if (ChangeSetType.UPDATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for existing stack %s %n", changeSetName, this.stack);
				if (templateBody != null && !templateBody.isEmpty()) {
					req.setTemplateBody(templateBody);
				} else if (templateUrl != null && !templateUrl.isEmpty()) {
					req.setTemplateURL(templateUrl);
				} else {
					req.setUsePreviousTemplate(true);
				}
			} else {
				throw new IllegalArgumentException("Cannot create a CloudFormation change set without a valid change set type.");
			}

			req.withParameters(params).withTags(tags).withRoleARN(roleArn);

			this.client.createChangeSet(req);

			new EventPrinter(this.client, this.listener).waitAndPrintChangeSetEvents(this.stack, changeSetName, this.client.waiters().changeSetCreateComplete(), pollIntervallMillis);

			this.listener.getLogger().format("Created CloudFormation change set %s for stack %s %n", changeSetName, this.stack);

		} catch (ExecutionException e) {
			try {
				if (this.changeSetExists(changeSetName) && !this.changeSetHasChanges(changeSetName)) {
					// Ignore the failed creation of a change set with no changes.
					this.listener.getLogger().format("Created empty change set %s for stack %s %n", changeSetName, this.stack);
					return;
				}
			} catch (Throwable throwable) {
				e.addSuppressed(throwable);
			}
			this.listener.getLogger().format("Failed to create CloudFormation change set %s for stack %s %n", changeSetName, this.stack);
			throw e;
		}
	}

	public void executeChangeSet(String changeSetName, long pollIntervallMillis) throws ExecutionException {
		if (!this.changeSetHasChanges(changeSetName)) {
			// If the change set has no changes we should simply delete it.
			this.listener.getLogger().format("Deleting empty change set %s for stack %s %n", changeSetName, this.stack);
			DeleteChangeSetRequest req = new DeleteChangeSetRequest().withChangeSetName(changeSetName).withStackName(this.stack);
			this.client.deleteChangeSet(req);
		} else {
			this.listener.getLogger().format("Executing change set %s for stack %s %n", changeSetName, this.stack);

			final Waiter<DescribeStacksRequest> waiter;
			if (this.exists()) {
				waiter = this.client.waiters().stackUpdateComplete();
			} else {
				waiter = this.client.waiters().stackCreateComplete();
			}

			ExecuteChangeSetRequest req = new ExecuteChangeSetRequest().withChangeSetName(changeSetName).withStackName(this.stack);
			this.client.executeChangeSet(req);
			new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, waiter, pollIntervallMillis);
			this.listener.getLogger().format("Executed change set %s for stack %s %n", changeSetName, this.stack);
		}
	}

	public void delete(long pollIntervallMillis) throws ExecutionException {
		this.client.deleteStack(new DeleteStackRequest().withStackName(this.stack));
		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackDeleteComplete(), pollIntervallMillis);
	}

	public DescribeChangeSetResult describeChangeSet(String changeSet) {
		return this.client.describeChangeSet(new DescribeChangeSetRequest()
				.withStackName(stack)
				.withChangeSetName(changeSet)
		);
	}
}
