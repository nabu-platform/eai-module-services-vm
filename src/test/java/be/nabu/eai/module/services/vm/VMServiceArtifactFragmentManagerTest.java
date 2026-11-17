package be.nabu.eai.module.services.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.validator.api.Validation;

public class VMServiceArtifactFragmentManagerTest {

	private static final String RESULT_NAME = "resultActiveParticipantState";

	private final VMServiceArtifactFragmentManager manager = new VMServiceArtifactFragmentManager();

	@Test
	public void duplicateInvokeResultNameIsRejectedForSameService() {
		Invoke first = invoke("bebatOne.services.organisation.updateSignatory");
		Invoke second = invoke("bebatOne.services.organisation.updateSignatory");
		List<Validation<?>> validations = validateUniqueInvokeResultNames(new Sequence(new Map(first, second)));

		assertEquals(1, validations.size());
		assertTrue(validations.get(0).getMessage().startsWith("invoke resultName '" + RESULT_NAME + "' is already used by another invoke"));
	}

	@Test
	public void duplicateInvokeResultNameIsRejectedAcrossSequentialMaps() {
		List<Validation<?>> validations = validateUniqueInvokeResultNames(new Sequence(
			new Map(invoke("example.first")),
			new Map(invoke("example.first"))));

		assertEquals(1, validations.size());
		assertTrue(validations.get(0).getMessage().startsWith("invoke resultName '" + RESULT_NAME + "' is already used by another invoke"));
	}

	@Test
	public void temporaryInvokeResultCanBeMappedWithinOwningMap() {
		Map map = mapWithInvoke(new Link(RESULT_NAME + "/active", "output/active"));

		assertTrue(validateDynamicInvokeResultUsage(new Sequence(map)).isEmpty());
	}

	@Test
	public void temporaryInvokeResultCannotBeUsedInAnotherMap() {
		Map sourceMap = mapWithInvoke();
		Map targetMap = new Map(new Link(RESULT_NAME + "/active", "output/active"));

		List<Validation<?>> validations = validateDynamicInvokeResultUsage(new Sequence(sourceMap, targetMap));

		assertEquals(1, validations.size());
		assertTrue(validations.get(0).getMessage().startsWith("dynamic invoke result '" + RESULT_NAME + "' may only be used within the map step that defines it"));
	}

	@Test
	public void temporaryInvokeResultCannotBeUsedByFixedValueLink() {
		Link link = new Link("=" + RESULT_NAME + "/active", "output/active");
		link.setFixedValue(true);
		Map map = mapWithInvoke(link);

		List<Validation<?>> validations = validateDynamicInvokeResultUsage(new Sequence(map));

		assertEquals(1, validations.size());
		assertTrue(validations.get(0).getMessage().startsWith("dynamic invoke result '" + RESULT_NAME + "' cannot be used by a fixed-value link"));
	}

	@Test
	public void sameMapInvokeResultPipelineRelayIsRejected() {
		Invoke producer = invoke(null);
		producer.setResultName("resultAction");
		Invoke consumer = invoke(null);
		consumer.setResultName("resultApplied");
		consumer.setInvocationOrder(1);
		consumer.getChildren().add(new Link("expiryActionId", "expiryActionId"));
		Map map = new Map(producer, consumer, new Link("resultAction/expiryActionId", "expiryActionId"));

		List<Validation<?>> validations = validateMapPipelineReadWriteConflicts(new Sequence(map));

		assertEquals(1, validations.size());
		assertTrue(validations.get(0).getMessage().contains("invoke result write to 'expiryActionId' conflicts with invoke input read 'expiryActionId'"));
		assertTrue(validations.get(0).getMessage().contains("link the source invoke result directly to the target invoke input"));
		assertTrue(validations.get(0).getMessage().contains("target invocationOrder higher than the producer's"));
	}

	@Test
	public void orderedDirectInvokeResultLinkIsAccepted() {
		Invoke producer = invoke(null);
		producer.setResultName("resultAction");
		Invoke consumer = invoke(null);
		consumer.setResultName("resultApplied");
		consumer.setInvocationOrder(1);
		consumer.getChildren().add(new Link("resultAction/expiryActionId", "expiryActionId"));

		Sequence sequence = new Sequence(new Map(producer, consumer));
		assertTrue(validateMapPipelineReadWriteConflicts(sequence).isEmpty());
		List<Validation<?>> orderValidations = new ArrayList<Validation<?>>();
		manager.validateInvocationOrder(sequence, orderValidations);
		assertTrue(orderValidations.isEmpty());
	}

	@Test
	public void invokeResultPipelineStagingForLaterMapAndOutputIsAccepted() {
		Invoke producer = invoke(null);
		producer.setResultName("resultAction");
		Map first = new Map(producer,
			new Link("resultAction/expiryActionId", "expiryActionId"),
			new Link("resultAction/expiryActionId", "output/expiryActionId"));
		Invoke consumer = invoke(null);
		consumer.setResultName("resultApplied");
		consumer.getChildren().add(new Link("expiryActionId", "expiryActionId"));

		assertTrue(validateMapPipelineReadWriteConflicts(new Sequence(first, new Map(consumer))).isEmpty());
	}

	private Map mapWithInvoke(Step... additionalSteps) {
		Invoke invoke = invoke(null);
		Step[] steps = new Step[additionalSteps.length + 1];
		steps[0] = invoke;
		System.arraycopy(additionalSteps, 0, steps, 1, additionalSteps.length);
		return new Map(steps);
	}

	private Invoke invoke(String serviceId) {
		Invoke invoke = new Invoke();
		invoke.setServiceId(serviceId);
		invoke.setResultName(RESULT_NAME);
		return invoke;
	}

	private List<Validation<?>> validateUniqueInvokeResultNames(Sequence sequence) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		manager.validateUniqueInvokeResultNames(sequence, validations);
		return validations;
	}

	private List<Validation<?>> validateDynamicInvokeResultUsage(Sequence sequence) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		manager.validateDynamicInvokeResultUsage(sequence, validations);
		return validations;
	}

	private List<Validation<?>> validateMapPipelineReadWriteConflicts(Sequence sequence) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		manager.validateMapPipelineReadWriteConflicts(sequence, validations);
		return validations;
	}
}
