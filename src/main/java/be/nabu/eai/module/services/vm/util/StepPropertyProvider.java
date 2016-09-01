package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdater;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.vm.step.Break;
import be.nabu.libs.services.vm.step.Catch;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.BaseProperty;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.validator.RangeValidator;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.Validator;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class StepPropertyProvider implements PropertyUpdater {
	
	private TreeCell<Step> cell;
	private Step step;
	
	public StepPropertyProvider(TreeCell<Step> step) {
		this.cell = step;
		this.step = this.cell.getItem().itemProperty().get();
	}

	@Override
	public Set<Property<?>> getSupportedProperties() {
		Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
		properties.add(new CommentProperty());
		properties.add(new LabelProperty());
		if (step instanceof Break) {
			properties.add(new BreakCountProperty());
		}
		else if (step instanceof Catch) {
			properties.add(new VariableProperty());
			properties.add(new ExceptionProperty());
		}
		else if (step instanceof For) {
			properties.add(new QueryProperty());
			properties.add(new VariableProperty());
			properties.add(new IndexProperty());
		}
		else if (step instanceof Switch) {
			properties.add(new QueryProperty());
		}
		else if (step instanceof Throw) {
			properties.add(new MessageProperty());
			properties.add(new CodeProperty());
		}
		else if (step instanceof Sequence) {
			properties.add(new TransactionVariableProperty());
			properties.add(new StepProperty());
		}
		return properties;
	}

	@Override
	public Value<?>[] getValues() {
		List<Value<?>> values = new ArrayList<Value<?>>();
		values.add(new ValueImpl<String>(new CommentProperty(), step.getComment()));
		values.add(new ValueImpl<String>(new LabelProperty(), step.getLabel()));
		if (step instanceof Break) {
			values.add(new ValueImpl<Integer>(new BreakCountProperty(), ((Break) step).getCount()));
		}
		else if (step instanceof Catch) {
			values.add(new ValueImpl<String>(new VariableProperty(), ((Catch) step).getVariable()));
			String types = null;
			for (Class<? extends Throwable> type : ((Catch) step).getTypes()) {
				if (types != null) {
					types += " | ";
				}
				types = types == null ? type.getName() : types + type.getName();
			}
			values.add(new ValueImpl<String>(new ExceptionProperty(), types));
		}
		else if (step instanceof For) {
			values.add(new ValueImpl<String>(new QueryProperty(), ((For) step).getQuery()));
			values.add(new ValueImpl<String>(new VariableProperty(), ((For) step).getVariable()));
			values.add(new ValueImpl<String>(new IndexProperty(), ((For) step).getIndex()));
		}
		else if (step instanceof Switch) {
			values.add(new ValueImpl<String>(new QueryProperty(), ((Switch) step).getQuery()));
		}
		else if (step instanceof Throw) {
			values.add(new ValueImpl<String>(new MessageProperty(), ((Throw) step).getMessage()));
			values.add(new ValueImpl<String>(new CodeProperty(), ((Throw) step).getCode()));
		}
		else if (step instanceof Sequence) {
			values.add(new ValueImpl<String>(new TransactionVariableProperty(), ((Sequence) step).getTransactionVariable()));
			values.add(new ValueImpl<String>(new StepProperty(), ((Sequence) step).getStep()));
		}
		return values.toArray(new Value[0]);
	}

	@Override
	public boolean canUpdate(Property<?> property) {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<ValidationMessage> updateProperty(Property<?> property, Object value) {
		List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
		if (property instanceof CommentProperty) {
			step.setComment((String) value);
		}
		else if (property instanceof LabelProperty) {
			step.setLabel((String) value);
		}
		else if (step instanceof Break) {
			((Break) step).setCount(value == null ? 1 : (Integer) value);
		}
		else if (step instanceof Catch) {
			if (property instanceof VariableProperty) {
				((Catch) step).setVariable((String) value);
			}
			else if (property instanceof ExceptionProperty) {
				List<Class<? extends Exception>> classes = new ArrayList<Class<? extends Exception>>();
				for (String type : ((String) value).split("\\|")) {
					try {
						classes.add((Class<? extends Exception>) Class.forName(type.trim()));
					}
					catch (ClassNotFoundException e) {
						messages.add(new ValidationMessage(Severity.ERROR, "The throwable type " + type + " is incorrect"));
					}
				}
				((Catch) step).setTypes(classes);
			}
		}
		else if (step instanceof For) {
			if (property instanceof QueryProperty) {
				((For) step).setQuery((String) value);
			}
			else if (property instanceof VariableProperty) {
				((For) step).setVariable((String) value);
			}
			else if (property instanceof IndexProperty) {
				((For) step).setIndex((String) value);
			}
		}
		else if (step instanceof Switch) {
			((Switch) step).setQuery((String) value);
		}
		else if (step instanceof Throw) {
			if (property instanceof MessageProperty) {
				((Throw) step).setMessage((String) value);
			}
			else if (property instanceof CodeProperty) {
				((Throw) step).setCode((String) value);
			}
		}
		else if (step instanceof Sequence) {
			if (property instanceof TransactionVariableProperty) {
				((Sequence) step).setTransactionVariable((String) value);
			}
			else if (property instanceof StepProperty) {
				((Sequence) step).setStep((String) value);
			}
		}
		cell.refresh();
		MainController.getInstance().setChanged();
		return messages;
	}
	
	public static class BreakCountProperty extends BaseProperty<Integer> {
		@Override
		public String getName() {
			return "breakCount";
		}
		@Override
		public Validator<Integer> getValidator() {
			return new RangeValidator<Integer>(0, true, null, true);
		}
		@Override
		public Class<Integer> getValueClass() {
			return Integer.class;
		}
	}
	
	public static class QueryProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "query";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class LabelProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "label";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class VariableProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "variable";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class TransactionVariableProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "transactionVariable";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}

	public static class StepProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "step";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class ExceptionProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "exceptions";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class IndexProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "index";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class MessageProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "message";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}
	
	public static class CodeProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "code";
		}
		@Override
		public Validator<String> getValidator() {
			return null;
		}
		@Override
		public Class<String> getValueClass() {
			return String.class;
		}
	}

	@Override
	public boolean isMandatory(Property<?> property) {
		return false;
	}
}
