package be.nabu.eai.module.services.vm;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import be.nabu.eai.developer.MainController;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeCellValue;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class StepFactory implements Callback<TreeItem<Step>, TreeCellValue<Step>> {
	
	private ObservableList<Validation<?>> validations;

	public StepFactory(ObservableList<Validation<?>> validations) {
		this.validations = validations;
	}
	
	@Override
	public TreeCellValue<Step> call(TreeItem<Step> treeItem) {
		return new StepCell(treeItem, validations);
	}
	
	public static class StepCell implements TreeCellValue<Step> {

		private ObjectProperty<TreeCell<Step>> cell = new SimpleObjectProperty<TreeCell<Step>>();
		private HBox box = new HBox();
		private HBox validationsBox = new HBox();
		private List<Validation<?>> localMessages = new ArrayList<Validation<?>>();
		private ListChangeListener<Validation<?>> listChangeListener;
		
		public StepCell(TreeItem<Step> treeItem, ObservableList<Validation<?>> validations) {
			refresh(treeItem.itemProperty().get());
			listChangeListener = new ListChangeListener<Validation<?>>() {
				@Override
				public void onChanged(ListChangeListener.Change<? extends Validation<?>> change) {
					while (change.next()) {
						if (change.wasRemoved()) {
							for (Validation<?> value : change.getRemoved()) {
								StepCell.this.localMessages.remove(value);
							}
						}
						if (change.wasAdded()) {
							for (Validation<?> value : change.getAddedSubList()) {
								for (Object context : value.getContext()) {
									if (context.toString().contains(treeItem.itemProperty().get().getId())) {
										StepCell.this.localMessages.add(value);
									}
								}
							}
						}
						if (change.wasUpdated() || change.wasReplaced()) {
							throw new RuntimeException("Not expecting updated or replaced: " + change.getList());
						}
					}
					drawValidations(treeItem.itemProperty().get());
				}
			};
			validations.addListener(listChangeListener);
			for (Validation<?> value : validations) {
				for (Object context : value.getContext()) {
					if (context.toString().contains(treeItem.itemProperty().get().getId())) {
						localMessages.add(value);
					}
				}
			}
			if (!localMessages.isEmpty()) {
				drawValidations(treeItem.itemProperty().get());
			}
		}
		
		@Override
		public Region getNode() {
			return box;
		}

		@Override
		public ObjectProperty<TreeCell<Step>> cellProperty() {
			return cell;
		}

		@Override
		public void refresh() {
			if (cell.get() != null) {
				Step step = cell.get().getItem().itemProperty().get();
				refresh(step);
			}
		}

		private HBox drawValidations(Step step) {
			validationsBox.getChildren().clear();
			String message = null;
			Severity severity = null;
			for (Validation<?> validation : localMessages) {
				if (validation.getSeverity() == Severity.INFO) {
					continue;
				}
				if (severity == null || validation.getSeverity().ordinal() > severity.ordinal()) {
					severity = validation.getSeverity();
				}
				if (message == null) {
					message = "";
				}
				else {
					message += "\n";
				}
				message += validation.getMessage();
			}
			if (severity != null) {
				Node graphic = MainController.loadFixedSizeGraphic("severity-" + severity.name().toLowerCase() + ".png");
				Tooltip tooltip = new Tooltip(message);
				Tooltip.install(graphic, tooltip);
				validationsBox.getChildren().add(graphic);
			}
			return validationsBox;
		}
		
		private void refresh(Step step) {
			box.getChildren().clear();
			box.getChildren().add(validationsBox);
			
			drawStep(step, box);
		}

		public static void drawStep(Step step, HBox box) {
			String labelText = step.getLabel();
			if (labelText == null && step.getParent() instanceof Switch) {
				labelText = "$default";
			}
			
			if (labelText != null) {
				Label label = new Label(labelText);
				label.getStyleClass().add("vm-label");
				box.getChildren().addAll(label);
			}
			
			String comment = step.getComment();
			if (comment == null && step instanceof Map) {
				Invoke invoke = null;
				for (Step child : ((Map) step).getChildren()) {
					if (child instanceof Invoke) {
						if (invoke == null) {
							invoke = (Invoke) child;
						}
						else {
							invoke = null;
							break;
						}
					}
				}
				if (invoke != null) {
					comment = invoke.getServiceId();
				}
			}
			
			// only show the step name if explicitly set or if there is no comment and label present
			if (step.getName() != null || (step.getLabel() == null && comment == null) || (labelText == null && comment == null) || step instanceof Switch || (step instanceof Throw && ((Throw) step).getMessage() == null)) {
				Label name = new Label(step.getName() == null ? step.getClass().getSimpleName() : step.getName());
				name.getStyleClass().add("vm-name");
				box.getChildren().addAll(name);
			}
			
			if (step instanceof For) {
				boolean hasVariable = false;
				if (((For) step).getVariable() != null) {
					hasVariable = true;
					Label each = new Label("each");
					each.getStyleClass().add("vm-description");
					if (step.getLabel() != null) {
						each.getStyleClass().add("vm-margin-left");
					}
					Label item = new Label(((For) step).getVariable());
					item.getStyleClass().add("vm-value");
					box.getChildren().addAll(each, item);
				}
				if (((For) step).getQuery() != null) {
					Label in = new Label(hasVariable ? "in" : "each");
					in.getStyleClass().add("vm-description");
					
					Label list = new Label(((For) step).getQuery());
					list.getStyleClass().add("vm-value");
					box.getChildren().addAll(in, list);
				}
			}
			else if (step instanceof Switch) {
				if (((Switch) step).getQuery() != null) {
					Label on = new Label("on");
					on.getStyleClass().add("vm-description");
					
					Label query = new Label(((Switch) step).getQuery());
					query.getStyleClass().add("vm-value");
					
					box.getChildren().addAll(on, query);
				}
			}
			else if (step instanceof Throw) {
				if (((Throw) step).getCode() != null) {
					Label code = new Label("[" + ((Throw) step).getCode() + "]");
					code.getStyleClass().addAll("vm-value", "vm-throw-code");
					box.getChildren().addAll(code);
				}
				if (((Throw) step).getMessage() != null) {
					Label message = new Label(((Throw) step).getMessage());
					message.getStyleClass().addAll("vm-value", "vm-throw-message");
					box.getChildren().addAll(message);
					
					if (((Throw) step).getCode() == null && step.getLabel() != null) {
						message.getStyleClass().add("vm-margin-left");
					}
				}
			}
			
			if (comment != null) {
				Label label = new Label(comment);
				label.getStyleClass().add("vm-comment");
				if (labelText != null) {
					label.getStyleClass().add("vm-margin-left");
				}
				box.getChildren().addAll(label);
			}
		}
		
	}
	
}
