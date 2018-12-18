package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.DescribeStackDriftDetectionStatusRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackDriftDetectionStatusResult;
import com.amazonaws.services.cloudformation.model.StackDriftDetectionStatus;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterAcceptor;
import com.amazonaws.waiters.WaiterBuilder;
import com.amazonaws.waiters.WaiterState;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Value
@Builder
public class CustomCloudformationWaiters {
	@NonNull
	AmazonCloudFormation client;
	@NonNull
	ExecutorService executorService;

	public static CustomCloudformationWaiters of(AmazonCloudFormation client) {
		return builder()
				.client(client)
				.executorService(Executors.newFixedThreadPool(50))
				.build();
	}

	public Waiter<DescribeStackDriftDetectionStatusRequest> stackDriftComplete() {
		return new WaiterBuilder<DescribeStackDriftDetectionStatusRequest, DescribeStackDriftDetectionStatusResult>()
				.withSdkFunction(this.client::describeStackDriftDetectionStatus)
				.withAcceptors(new WaiterAcceptor<DescribeStackDriftDetectionStatusResult>() {
					@Override
					public WaiterState getState() {
						return WaiterState.SUCCESS;
					}

					@Override
					public boolean matches(DescribeStackDriftDetectionStatusResult describeStackDriftDetectionStatusResult) {
						return describeStackDriftDetectionStatusResult.getDetectionStatus().equals(StackDriftDetectionStatus.DETECTION_COMPLETE.name()) ||
						describeStackDriftDetectionStatusResult.getDetectionStatus().equals(StackDriftDetectionStatus.DETECTION_FAILED.name());
					}

				})
				.build();
	}
}
