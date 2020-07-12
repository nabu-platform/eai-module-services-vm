package be.nabu.eai.module.services.vm.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.MainController.PropertyUpdaterWithSource;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.repository.api.Repository;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.libs.property.api.ComparableProperty;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.vm.step.Break;
import be.nabu.libs.services.vm.step.Catch;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.BaseProperty;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.SimpleProperty;
import be.nabu.libs.validator.RangeValidator;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.Validator;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class StepPropertyProvider implements PropertyUpdaterWithSource {
	
	private TreeCell<Step> cell;
	private Step step;
	private Repository repository;
	private String sourceId;
	
	public StepPropertyProvider(TreeCell<Step> step, Repository repository, String sourceId) {
		this.cell = step;
		this.repository = repository;
		this.sourceId = sourceId;
		this.step = this.cell.getItem().itemProperty().get();
	}

	@Override
	public Set<Property<?>> getSupportedProperties() {
		Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
		properties.add(CommentProperty.getInstance());
		// can't set a label if there is no parent as there is no one to execute it, note that basestep validation will throw a validation error if you set it (no parent pipeline to retrieve)
		if (step.getParent() != null) {
			properties.add(new LabelProperty());
			properties.add(new FeatureProperty());
		}
		properties.add(new StepNameProperty());
		properties.add(new DescriptionProperty());
		if (step instanceof Break) {
			properties.add(new BreakCountProperty());
		}
		else if (step instanceof Catch) {
			properties.add(new VariableProperty());
			properties.add(new ExceptionProperty());
			properties.add(new SuppressExceptionProperty());
			properties.add(new CodeProperty());
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
			properties.add(new CodeProperty());
			properties.add(new MessageProperty());
			properties.add(new AliasProperty());
			properties.add(new RealmProperty());
		}
		else if (step instanceof Sequence) {
			properties.add(new TransactionVariableProperty());
		}
		return properties;
	}

	@Override
	public Value<?>[] getValues() {
		List<Value<?>> values = new ArrayList<Value<?>>();
		values.add(new ValueImpl<String>(new CommentProperty(), step.getComment()));
		values.add(new ValueImpl<String>(new FeatureProperty(), step.getFeatures()));
		values.add(new ValueImpl<String>(new LabelProperty(), step.getLabel()));
		values.add(new ValueImpl<String>(new StepNameProperty(), step.getName()));
		values.add(new ValueImpl<String>(new DescriptionProperty(), step.getDescription()));
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
			values.add(new ValueImpl<Boolean>(new SuppressExceptionProperty(), ((Catch) step).getSuppressException()));
			String codes = null;
			if (((Catch) step).getCodes() != null) {
				for (String code : ((Catch) step).getCodes()) {
					if (code != null) { 
						if (codes == null) {
							codes = "";
						}
						else {
							codes += ", ";
						}
						codes += code;
					}
				}
			}
			values.add(new ValueImpl<String>(new CodeProperty(), codes));
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
			values.add(new ValueImpl<String>(new CodeProperty(), ((Throw) step).getCode()));
			values.add(new ValueImpl<String>(new MessageProperty(), ((Throw) step).getMessage()));
			values.add(new ValueImpl<String>(new AliasProperty(), ((Throw) step).getAlias()));
			values.add(new ValueImpl<String>(new RealmProperty(), ((Throw) step).getRealm()));
		}
		else if (step instanceof Sequence) {
			values.add(new ValueImpl<String>(new TransactionVariableProperty(), ((Sequence) step).getTransactionVariable()));
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
		// when we rename an old variable (e.g. the for loop variable), we check that the variable does not exist in the parent before renaming it
		// otherwise the variable defined by the for loop is invalid and not the one you meant
		
		List<ValidationMessage> messages = new ArrayList<ValidationMessage>();
		if (property instanceof CommentProperty) {
			step.setComment((String) value);
		}
		else if (property instanceof DescriptionProperty) {
			step.setDescription((String) value);
		}
		else if (property instanceof FeatureProperty) {
			step.setFeatures((String) value);
		}
		else if (property instanceof LabelProperty) {
			step.setLabel((String) value);
		}
		else if (property instanceof StepNameProperty) {
			step.setName((String) value);
		}
		else if (step instanceof Break) {
			((Break) step).setCount(value == null ? 1 : (Integer) value);
		}
		else if (step instanceof Catch) {
			if (property instanceof VariableProperty) {
				String variableName = (String) value;
				if (variableName == null || ElementTreeItem.isValidName(variableName)) {
					if (((Catch) step).getVariable() != null && value != null && !parentHasVariable(step, ((Catch) step).getVariable())) {
						ElementTreeItem.renameVariable(MainController.getInstance(), ((Catch) step).getVariable(), variableName);
					}
					((Catch) step).setVariable(variableName);
				}
				else {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "The variable name '" + variableName + "' is not a valid name"));
				}
			}
			else if (property instanceof ExceptionProperty) {
				List<Class<? extends Exception>> classes = new ArrayList<Class<? extends Exception>>();
				if (value != null && !((String) value).isEmpty()) {
					for (String type : ((String) value).split("\\|")) {
						try {
							classes.add((Class<? extends Exception>) Thread.currentThread().getContextClassLoader().loadClass(type.trim()));
						}
						catch (ClassNotFoundException e) {
							messages.add(new ValidationMessage(Severity.ERROR, "The throwable type " + type + " is incorrect"));
						}
					}
				}
				((Catch) step).setTypes(classes);
			}
			else if (property instanceof SuppressExceptionProperty) {
				((Catch) step).setSuppressException((Boolean) value);
			}
			else if (property instanceof CodeProperty) {
				((Catch) step).setCodes(value == null || value.toString().trim().isEmpty() ? null : new ArrayList<String>(Arrays.asList(value.toString().split("[\\s]*,[\\s]*"))));
			}
		}
		else if (step instanceof For) {
			if (property instanceof QueryProperty) {
				((For) step).setQuery((String) value);
			}
			else if (property instanceof VariableProperty) {
				String variableName = (String) value;
				if (variableName == null || ElementTreeItem.isValidName(variableName)) {
					if (((For) step).getVariable() != null && value != null && !parentHasVariable(step, ((For) step).getVariable())) {
						ElementTreeItem.renameVariable(MainController.getInstance(), ((For) step).getVariable(), variableName);
					}
					((For) step).setVariable(variableName);
				}
				else {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "The variable name '" + variableName + "' is not a valid name"));
				}
			}
			else if (property instanceof IndexProperty) {
				String variableName = (String) value;
				if (variableName == null || ElementTreeItem.isValidName(variableName)) {
					if (((For) step).getIndex() != null && value != null && !parentHasVariable(step, ((For) step).getIndex())) {
						ElementTreeItem.renameVariable(MainController.getInstance(), ((For) step).getIndex(), variableName);
					}
					((For) step).setIndex(variableName);
				}
				else {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "The variable name '" + variableName + "' is not a valid name"));
				}
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
			else if (property instanceof AliasProperty) {
				((Throw) step).setAlias((String) value);
			}
			else if (property instanceof RealmProperty) {
				((Throw) step).setRealm((String) value);
			}
		}
		else if (step instanceof Sequence) {
			if (property instanceof TransactionVariableProperty) {
				String variableName = (String) value;
				if (variableName == null || ElementTreeItem.isValidName(variableName)) {
					if (((Sequence) step).getTransactionVariable() != null && value != null && !parentHasVariable(step, ((Sequence) step).getTransactionVariable())) {
						ElementTreeItem.renameVariable(MainController.getInstance(), ((Sequence) step).getTransactionVariable(), variableName);
					}
					((Sequence) step).setTransactionVariable(variableName);
				}
				else {
					MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "The variable name '" + variableName + "' is not a valid name"));
				}
			}
		}
		cell.refresh();
		MainController.getInstance().setChanged();
		return messages;
	}
	
	private boolean parentHasVariable(Step step, String variableName) {
		boolean has = false;
		if (step.getParent() != null) {
			ComplexType pipeline = step.getParent().getPipeline(MainController.getInstance().getRepository().getServiceContext());
			has = pipeline.get(variableName) != null;
		}
		return has;
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
			return "condition";
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
	
	public static class SuppressExceptionProperty extends BaseProperty<Boolean> {
		@Override
		public String getName() {
			return "suppressException";
		}
		@Override
		public Validator<Boolean> getValidator() {
			return null;
		}
		@Override
		public Class<Boolean> getValueClass() {
			return Boolean.class;
		}
	}

	public static class StepNameProperty extends BaseProperty<String> {
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
	
	public static class DescriptionProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "description";
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
	
	public static class AliasProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "alias";
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
	
	public static class RealmProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "realm";
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
	
	public static class FeatureProperty extends BaseProperty<String> {
		@Override
		public String getName() {
			return "feature";
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

	@Override
	public String getSourceId() {
		return sourceId;
	}

	@Override
	public Repository getRepository() {
		return repository;
	}
}
